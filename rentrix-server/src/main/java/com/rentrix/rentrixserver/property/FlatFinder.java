package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.location.NameMatch;
import com.rentrix.rentrixserver.location.PlaceMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds a flat by the number typed after its society's name (D-164): "1204", "Flat 1204", "B-1204" or "B1204" when
 * the flat is 1204 in Tower B or Wing B. Only flats whose number is {@code OPEN} (D-140) are ever compared, so the
 * answer is the same whether or not a masked flat with that number exists: a masked flat number cannot be probed
 * (D-57).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlatFinder {

	private final PropertyRepository properties;

	/** A society, or one tower of it ({@code towerId} not null), to look for a flat in. */
	public record Place(Long societyId, Long towerId) {
		
		public static Place of(PlaceMatcher.SocietyHit hit) {
			return new Place(hit.society().getId(), hit.tower() == null ? null : hit.tower().getId());
		}
		
	}
	
	/** {@link #find(List, String, boolean, int)} for the places of matcher hits. */
	public List<Property> find(List<PlaceMatcher.SocietyHit> hits, String unit, boolean prefix, int limit) {
		return findIn(hits.stream().map(Place::of).toList(), unit, prefix, limit);
	}
	
	/**
	 * The open flats of {@code places} whose number is {@code unit} ({@code prefix}: starts with it), in flat-number
	 * order, at most {@code limit}.
	 */
	public List<Property> findIn(List<Place> places, String unit, boolean prefix, int limit) {
		String typed = key(unit);
		if (places.isEmpty() || typed.isEmpty()) {
			return List.of();
		}
		Map<Long, List<Place>> bySociety = places.stream().collect(Collectors.groupingBy(Place::societyId));
		List<Property> found = new ArrayList<>();
		for (Property home : open(bySociety.keySet())) {
			boolean inPlace = bySociety.get(home.getSociety().getId()).stream().anyMatch(place -> place.towerId() == null
				|| home.getTower() != null && place.towerId().equals(home.getTower().getId()));
			if (inPlace && keys(home).stream().anyMatch(k -> prefix ? k.startsWith(typed) : k.equals(typed))) {
				found.add(home);
			}
		}
		found.sort(Comparator.comparing((Property p) -> key(p.getUnitLabel())).thenComparing(Property::getId));
		return found.size() > limit ? List.copyOf(found.subList(0, limit)) : List.copyOf(found);
	}
	
	private List<Property> open(Collection<Long> societyIds) {
		return properties.findBySocietyIdInAndStatusAndUnitLabelVisibilityAndDeletedAtIsNull(societyIds,
			PropertyStatus.PUBLISHED, FieldVisibility.OPEN);
	}

	/** Letters and digits of a flat number, lower case, without a leading "Flat" / "Unit" / "No.". */
	static String key(String unit) {
		List<String> words = new ArrayList<>(NameMatch.words(unit));
		while (words.size() > 1 && NameMatch.UNIT_WORDS.contains(words.getFirst())) {
			words.removeFirst();
		}
		return String.join("", words);
	}

	/**
	 * The flat number alone, with its tower and / or wing in front, and without them when the number already starts
	 * with them: flat 1204 of Tower B is 1204 and b1204; flat "B-1204" of Tower B is b1204 and 1204.
	 */
	static List<String> keys(Property home) {
		String unit = key(home.getUnitLabel());
		if (unit.isEmpty()) {
			return List.of();
		}
		String tower = home.getTower() == null ? "" : level(home.getTower().getName());
		String wing = home.getWing() == null ? "" : level(home.getWing().getName());
		List<String> keys = new ArrayList<>(List.of(unit));
		for (String level : List.of(tower, wing, tower + wing)) {
			if (level.isEmpty()) {
				continue;
			}
			keys.add(level + unit);
			if (unit.length() > level.length() && unit.startsWith(level)) {
				keys.add(unit.substring(level.length()));
			}
		}
		return keys;
	}

	private static String level(String name) {
		return String.join("", NameMatch.withoutLevelWords(NameMatch.words(name)));
	}

}
