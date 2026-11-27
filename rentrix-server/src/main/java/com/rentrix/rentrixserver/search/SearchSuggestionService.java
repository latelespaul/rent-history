package com.rentrix.rentrixserver.search;

import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.location.LocationService;
import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.NameMatch;
import com.rentrix.rentrixserver.location.PlaceMatcher;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.dto.AreaRef;
import com.rentrix.rentrixserver.property.FlatFinder;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The search box suggestions (D-164): typo-tolerant word prefixes of society, tower and area names
 * ({@link PlaceMatcher}), and the open flats of the named society when the text ends in a flat number
 * ({@link FlatFinder}, prefix match so "Lakeside 12" offers 1204 and 1205).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchSuggestionService {
	
	static final int MAX_SOCIETIES = 8;
	static final int MAX_AREAS = 5;
	static final int MAX_HOMES = 8;
	
	/** Fewer typed letters than this suggest nothing. */
	static final int MIN_LETTERS = 2;
	
	private final PlaceMatcher matcher;
	private final FlatFinder flats;
	private final CityService cities;
	private final LocationService locations;
	
	public SearchSuggestions suggest(List<String> cityCodes, String text, AuthenticatedUser caller) {
		cityCodes = cities.liveSearchCodes(cityCodes);
		if (String.join("", NameMatch.words(text)).length() < MIN_LETTERS) {
			return SearchSuggestions.NONE;
		}
		PlaceMatcher.Query query = PlaceMatcher.Query.parse(text);
		// "Sector 21 Heights" ends in a number too: the whole text is tried as a place name first
		List<PlaceMatcher.SocietyHit> societies = matcher.societies(cityCodes, PlaceMatcher.Query.whole(text), caller,
			MAX_SOCIETIES);
		List<PlaceMatcher.SocietyHit> named = query.unit() == null ? List.of()
			: matcher.societies(cityCodes, query, caller, MAX_SOCIETIES);
		if (!PlaceMatcher.unitFirst(named, societies, query.unit())) {
			named = List.of();
		}
		List<SearchSuggestions.HomeSuggestion> homes = flats.find(named, query.unit(), true, MAX_HOMES).stream()
			.map(SearchSuggestionService::home).toList();
		if (societies.isEmpty()) {
			societies = named;
		}
		List<SearchSuggestions.AreaSuggestion> areas = matcher.areas(cityCodes, text, caller, MAX_AREAS).stream()
			.map(hit -> new SearchSuggestions.AreaSuggestion(hit.area().getPublicId(), hit.area().getName(),
				CityRef.of(hit.area().getCity())))
			.toList();
		return new SearchSuggestions(societies.stream().map(SearchSuggestionService::society).toList(), areas, homes);
	}
	
	/**
	 * The second step of the box (D-167): with a society picked, {@code text} is a flat number or a tower. Suggests the
	 * society's towers whose name the text starts (every tower with a published home when it is empty) and its open
	 * flats whose number starts with it (the first ones when empty). Only {@code OPEN} flat numbers (D-140); the
	 * society must be readable (D-109, D-166), else 404.
	 */
	public SearchSuggestions suggestInSociety(UUID societyId, String text, AuthenticatedUser caller) {
		Society society = locations.readableSociety(societyId, caller);
		List<String> typed = NameMatch.withoutLevelWords(NameMatch.words(text));
		List<SearchSuggestions.TowerSuggestion> towers = locations.towers(societyId, caller).stream()
			.filter(t -> t.publishedHomes() > 0)
			.filter(t -> typed.isEmpty() || NameMatch.cost(typed,
				NameMatch.withoutLevelWords(NameMatch.words(t.name()))) != NameMatch.NONE)
			.map(t -> new SearchSuggestions.TowerSuggestion(t.id(), t.name(), t.publishedHomes()))
			.toList();
		List<Property> found = typed.isEmpty() && NameMatch.words(text).isEmpty()
			? flats.openIn(society.getId(), MAX_HOMES)
			: flats.findIn(List.of(new FlatFinder.Place(society.getId(), null)), text, true, MAX_HOMES);
		return new SearchSuggestions(List.of(), List.of(), found.stream().map(SearchSuggestionService::home).toList(),
			towers);
	}
	
	private static SearchSuggestions.SocietySuggestion society(PlaceMatcher.SocietyHit hit) {
		Society s = hit.society();
		return new SearchSuggestions.SocietySuggestion(s.getPublicId(), s.getName(), AreaRef.of(s.getArea()),
			CityRef.of(s.getCity()),
			hit.tower() == null ? null : new SearchSuggestions.Level(hit.tower().getPublicId(), hit.tower().getName()),
			hit.publishedHomes(), s.getStatus() == LocationStatus.PENDING
				|| hit.tower() != null && hit.tower().getStatus() == LocationStatus.PENDING);
	}
	
	private static SearchSuggestions.HomeSuggestion home(Property p) {
		return new SearchSuggestions.HomeSuggestion(p.getPublicId(),
			new SearchSuggestions.Level(p.getSociety().getPublicId(), p.getSociety().getName()),
			p.getTower() == null ? null : new SearchSuggestions.Level(p.getTower().getPublicId(), p.getTower().getName()),
			p.getWing() == null ? null : new SearchSuggestions.Level(p.getWing().getPublicId(), p.getWing().getName()),
			p.getUnitLabel(), p.getLocality(), CityRef.of(p.getCity()));
	}
	
}
