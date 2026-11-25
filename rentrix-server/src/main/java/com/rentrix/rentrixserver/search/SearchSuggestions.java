package com.rentrix.rentrixserver.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.location.dto.AreaRef;
import com.rentrix.rentrixserver.property.dto.CityRef;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /search/suggestions} (D-164): what the search box offers while the user types. Societies first (with
 * the tower when the text names one), then areas, then flats when the text ends in a flat number. Only flats whose
 * number is open (D-140) are ever offered, so {@code unitLabel} is always one anybody may see (D-57).
 */
public record SearchSuggestions(List<SocietySuggestion> societies, List<AreaSuggestion> areas,
	List<HomeSuggestion> homes, List<TowerSuggestion> towers) {
	
	public static final SearchSuggestions NONE = new SearchSuggestions(List.of(), List.of(), List.of(), List.of());
	
	/** Suggestions without towers: the box before a society is picked. */
	public SearchSuggestions(List<SocietySuggestion> societies, List<AreaSuggestion> areas, List<HomeSuggestion> homes) {
		this(societies, areas, homes, List.of());
	}
	
	/** A tower of the picked society (D-167), with its published homes; opens the society page on that tower. */
	public record TowerSuggestion(UUID id, String name, long publishedHomes) {
	}
	
	/** A society, with {@code tower} when the text also named one of its towers. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SocietySuggestion(UUID id, String name, AreaRef area, CityRef city, Level tower,
		long publishedHomes, boolean pending) {
	}
	
	public record AreaSuggestion(UUID id, String name, CityRef city) {
	}
	
	/** One flat: a property ({@code id}, opens {@code /p/:id}) and its place inside the society. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record HomeSuggestion(UUID id, Level society, Level tower, Level wing, String unitLabel, String locality,
		CityRef city) {
	}
	
	public record Level(UUID id, String name) {
	}
	
}
