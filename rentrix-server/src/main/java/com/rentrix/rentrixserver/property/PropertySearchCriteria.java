package com.rentrix.rentrixserver.property;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validated filters of {@code GET /properties} (§12.2). Every field is optional; {@code cityCodes} holds the
 * cities to search, any of them (captain 2026-11-27), and an empty list means every live city. {@code localityPrefix} and {@code text} are already normalised like {@link Property#localityKey}.
 * The location filters (D-112) are internal ids, already resolved and checked by the service: {@code societyId}
 * (with an optional {@code towerId} of that society) or else {@code areaId} replaces the city range;
 * {@code standalone} keeps homes outside ({@code true}) or inside ({@code false}) a society.
 */
public record PropertySearchCriteria(
	List<String> cityCodes,
	String localityPrefix,
	PropertyType type,
	Integer bedrooms,
	BigDecimal minRating,
	String text,
	Long areaId,
	Long societyId,
	Long towerId,
	Boolean standalone,
	/** {@code GET /properties?homeSize=} (captain 2026-11-23 17:18). */
	HomeSize homeSize,
	/** What the service resolved {@code text} to (D-164); null searches the text alone. */
	TextPlaces textPlaces
) {
	
	/**
	 * The places and flats {@code text} names (D-164), as internal ids. With {@code homeIds} the text found those
	 * flats directly and the result is exactly them; otherwise homes in {@code societyIds} or {@code towerIds} are
	 * found next to the plain text matches.
	 */
	public record TextPlaces(List<Long> societyIds, List<Long> towerIds, List<Long> homeIds) {
		
		public static final TextPlaces NONE = new TextPlaces(List.of(), List.of(), List.of());
		
		public TextPlaces {
			societyIds = List.copyOf(societyIds);
			towerIds = List.copyOf(towerIds);
			homeIds = List.copyOf(homeIds);
		}
		
	}
	
	public PropertySearchCriteria {
		cityCodes = cityCodes == null ? List.of() : List.copyOf(cityCodes);
	}
	
	public PropertySearchCriteria(List<String> cityCodes, String localityPrefix, PropertyType type, Integer bedrooms,
		BigDecimal minRating, String text, Long areaId, Long societyId, Long towerId, Boolean standalone,
		HomeSize homeSize) {
		this(cityCodes, localityPrefix, type, bedrooms, minRating, text, areaId, societyId, towerId, standalone, homeSize,
			null);
	}
	
	public PropertySearchCriteria(String cityCode, String localityPrefix, PropertyType type, Integer bedrooms,
		BigDecimal minRating, String text, Long areaId, Long societyId, Long towerId, Boolean standalone) {
		this(cityCode == null ? List.of() : List.of(cityCode), localityPrefix, type, bedrooms, minRating, text, areaId,
			societyId, towerId, standalone, null);
	}
	
	/** The same filters with {@code text} resolved to {@code places}. */
	public PropertySearchCriteria withTextPlaces(TextPlaces places) {
		return new PropertySearchCriteria(cityCodes, localityPrefix, type, bedrooms, minRating, text, areaId, societyId,
			towerId, standalone, homeSize, places);
	}
	
	/** The same filters over {@code codes} instead. */
	public PropertySearchCriteria withCityCodes(List<String> codes) {
		return new PropertySearchCriteria(codes, localityPrefix, type, bedrooms, minRating, text, areaId, societyId,
			towerId, standalone, homeSize, textPlaces);
	}
	
	/** Filters without location hierarchy ids. */
	public PropertySearchCriteria(String cityCode, String localityPrefix, PropertyType type, Integer bedrooms,
		BigDecimal minRating, String text) {
		this(cityCode, localityPrefix, type, bedrooms, minRating, text, null, null, null, null);
	}
	
}
