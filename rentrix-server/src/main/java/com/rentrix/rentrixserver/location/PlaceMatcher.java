package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Finds the societies (with a tower) and areas a search box text names (D-164), typo-tolerant ({@link NameMatch}).
 * A text that ends in a flat number ("Prestige Lakeside 1204", "Lakeside B-1204") is split into the place words and
 * the {@link Query#unit}; the caller matches the unit against the society's flats. Candidates are the societies of
 * the city (every live city without one); a society is offered when it is {@code ACTIVE}, holds a published home
 * (whose card already names it, D-162) or the viewer may list it (D-109).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceMatcher {

	/** Upper bound on candidate rows per search; far above any city's societies today. */
	static final int MAX_CANDIDATES = 5000;

	private static final Pattern PINCODE = Pattern.compile("\\d{6}");

	private final SocietyRepository societies;
	private final SocietyTowerRepository towers;
	private final AreaRepository areas;

	/**
	 * The search box text split into place words and a trailing flat number: the last space-separated part is the
	 * unit when it holds a digit, is not a 6-digit PIN code and follows at least one place word.
	 */
	public record Query(List<String> placeWords, String unit) {

		public static Query parse(String text) {
			String trimmed = text == null ? "" : text.trim();
			String[] parts = trimmed.split("\\s+");
			if (parts.length >= 2) {
				String last = parts[parts.length - 1];
				if (last.chars().anyMatch(Character::isDigit) && !PINCODE.matcher(last).matches()) {
					List<String> place = new ArrayList<>(NameMatch.words(
						trimmed.substring(0, trimmed.length() - last.length())));
					while (!place.isEmpty() && NameMatch.UNIT_WORDS.contains(place.getLast())) {
						place.removeLast();
					}
					if (!place.isEmpty() && !NameMatch.words(last).isEmpty()) {
						return new Query(List.copyOf(place), last);
					}
				}
			}
			return new Query(NameMatch.words(trimmed), null);
		}

		/** The whole text as a place name, without a flat number: "Godrej 24" is a society too. */
		public static Query whole(String text) {
			return new Query(NameMatch.words(text), null);
		}

	}

	/**
	 * Whether the place words before the flat number {@code unit} ({@code split}) win over the whole text as a name
	 * ({@code whole}). Only a society whose own name holds the number exactly competes, and it wins on a tie: "Godrej
	 * 24" is that society, not flat 24 of another Godrej, while "Godrej 2" is still flat 2 and "Lakeside 1" flat 1, not
	 * "Godrej 24" or Tower 1. Both lists are best first.
	 */
	public static boolean unitFirst(List<SocietyHit> split, List<SocietyHit> whole, String unit) {
		if (split.isEmpty()) {
			return false;
		}
		String number = NameMatch.words(unit).getLast();
		return whole.stream()
			.filter(hit -> hit.tower() == null && NameMatch.words(hit.society().getName()).contains(number))
			.findFirst()
			.map(hit -> split.getFirst().cost() < hit.cost())
			.orElse(true);
	}

	/** One society the text names; {@code tower} when the words after the society's name name one of its towers. */
	public record SocietyHit(Society society, SocietyTower tower, int cost, long publishedHomes) {
	}

	/** One area the text names. */
	public record AreaHit(Area area, int cost) {
	}

	/**
	 * The societies {@code query}'s place words name, best first: fewest typos, then whole-name matches before
	 * tower matches, then the most published homes. {@code cityCodes} empty means every live city.
	 */
	public List<SocietyHit> societies(Collection<String> cityCodes, Query query, AuthenticatedUser caller, int limit) {
		List<String> typed = query.placeWords();
		if (typed.isEmpty()) {
			return List.of();
		}
		Viewer viewer = Viewer.of(caller);
		PageRequest bound = PageRequest.of(0, MAX_CANDIDATES);
		List<Society> candidates = cityCodes.isEmpty() ? societies.findCandidatesInLiveCities(bound)
			: societies.findCandidatesInCities(cityCodes, bound);
		warnIfCapped("societies", cityCodes, candidates.size());
		List<SocietyHit> whole = new ArrayList<>();
		// societies whose name takes the first k typed words; the rest may name a tower
		Map<Long, Integer> prefixCost = new HashMap<>();
		Map<Long, Integer> prefixLength = new HashMap<>();
		Map<Long, Society> byId = new HashMap<>();
		for (Society society : candidates) {
			List<String> name = NameMatch.words(society.getName());
			int cost = NameMatch.cost(typed, name);
			if (cost != NameMatch.NONE) {
				whole.add(new SocietyHit(society, null, cost, 0));
				byId.put(society.getId(), society);
				continue;
			}
			for (int k = typed.size() - 1; k >= 1; k--) {
				int head = NameMatch.cost(typed.subList(0, k), name);
				if (head != NameMatch.NONE) {
					prefixCost.put(society.getId(), head);
					prefixLength.put(society.getId(), k);
					byId.put(society.getId(), society);
					break;
				}
			}
		}
		List<SocietyHit> withTower = new ArrayList<>();
		Map<Long, Long> towerHomes = new HashMap<>();
		if (!prefixCost.isEmpty()) {
			List<SocietyTower> rows = towers.findBySocietyIdIn(prefixCost.keySet());
			counts(towerHomes, rows.isEmpty() ? List.of()
				: towers.countPublishedHomes(rows.stream().map(SocietyTower::getId).toList()));
			for (SocietyTower tower : rows) {
				Long societyId = tower.getSociety().getId();
				List<String> rest = NameMatch.withoutLevelWords(
					typed.subList(prefixLength.get(societyId), typed.size()));
				int cost = NameMatch.cost(rest, NameMatch.withoutLevelWords(NameMatch.words(tower.getName())));
				if (cost != NameMatch.NONE && offered(tower.getStatus(), tower.getCreatedBy(),
					towerHomes.getOrDefault(tower.getId(), 0L), viewer)) {
					withTower.add(new SocietyHit(byId.get(societyId), tower, prefixCost.get(societyId) + cost,
						towerHomes.getOrDefault(tower.getId(), 0L)));
				}
			}
		}
		Map<Long, Long> homes = new HashMap<>();
		List<Long> wholeIds = whole.stream().map(h -> h.society().getId()).toList();
		counts(homes, wholeIds.isEmpty() ? List.of() : societies.countPublishedHomes(wholeIds));
		List<SocietyHit> hits = new ArrayList<>();
		for (SocietyHit hit : whole) {
			long count = homes.getOrDefault(hit.society().getId(), 0L);
			if (offered(hit.society().getStatus(), hit.society().getCreatedBy(), count, viewer)) {
				hits.add(new SocietyHit(hit.society(), null, hit.cost(), count));
			}
		}
		for (SocietyHit hit : withTower) {
			if (hit.society().getStatus() == LocationStatus.ACTIVE || hit.publishedHomes() > 0
				|| viewer.lists(hit.society().getStatus(), hit.society().getCreatedBy())) {
				hits.add(hit);
			}
		}
		hits.sort(Comparator.comparingInt(SocietyHit::cost)
			.thenComparing(hit -> hit.tower() != null)
			.thenComparing(Comparator.comparingLong(SocietyHit::publishedHomes).reversed())
			.thenComparing(hit -> hit.society().getNameKey())
			.thenComparing(hit -> hit.society().getId()));
		return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : List.copyOf(hits);
	}

	/** The areas the whole text names by name or alias (D-108), best first; listed areas only (D-109). */
	public List<AreaHit> areas(Collection<String> cityCodes, String text, AuthenticatedUser caller, int limit) {
		List<String> typed = NameMatch.words(text);
		if (typed.isEmpty()) {
			return List.of();
		}
		Viewer viewer = Viewer.of(caller);
		PageRequest bound = PageRequest.of(0, MAX_CANDIDATES);
		List<Area> candidates = cityCodes.isEmpty() ? areas.findCandidatesInLiveCities(bound)
			: areas.findCandidatesInCities(cityCodes, bound);
		warnIfCapped("areas", cityCodes, candidates.size());
		List<AreaHit> hits = new ArrayList<>();
		for (Area area : candidates) {
			if (!viewer.lists(area.getStatus(), area.getCreatedBy())) {
				continue;
			}
			int cost = NameMatch.cost(typed, NameMatch.words(area.getName()));
			if (area.getAliases() != null) {
				for (String alias : area.getAliases().split(",")) {
					int aliasCost = NameMatch.cost(typed, NameMatch.words(alias));
					if (aliasCost != NameMatch.NONE && (cost == NameMatch.NONE || aliasCost < cost)) {
						cost = aliasCost;
					}
				}
			}
			if (cost != NameMatch.NONE) {
				hits.add(new AreaHit(area, cost));
			}
		}
		hits.sort(Comparator.comparingInt(AreaHit::cost).thenComparing(hit -> hit.area().getNameKey())
			.thenComparing(hit -> hit.area().getId()));
		return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : List.copyOf(hits);
	}

	/** {@code ACTIVE}, holding a published home (already public on its card), or listed for the viewer (D-109). */
	private static boolean offered(LocationStatus status, com.rentrix.rentrixserver.user.User createdBy,
		long publishedHomes, Viewer viewer) {
		return status == LocationStatus.ACTIVE || publishedHomes > 0 || viewer.lists(status, createdBy);
	}

	private static void warnIfCapped(String kind, Collection<String> cityCodes, int loaded) {
		if (loaded >= MAX_CANDIDATES) {
			log.warn("Place search loaded the full cap of {} {} candidates in {}; newer {} are not matched",
				MAX_CANDIDATES, kind, cityCodes.isEmpty() ? "all live cities" : String.join(",", cityCodes), kind);
		}
	}

	private static void counts(Map<Long, Long> into, List<Object[]> rows) {
		for (Object[] row : rows) {
			into.put((Long) row[0], ((Number) row[1]).longValue());
		}
	}

}
