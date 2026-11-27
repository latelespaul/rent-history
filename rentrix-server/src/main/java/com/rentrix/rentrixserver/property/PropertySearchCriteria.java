package com.rentrix.rentrixserver.property;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validated filters of {@code GET /properties} (§12.2). Every field is optional; a null city means every live
 * city. {@code localityPrefix} and {@code text} are already normalised like {@link Property#localityKey}.
 * The location filters (D-112) are internal ids, already resolved and checked by the service: {@code societyId}
 * (with an optional {@code towerId} of that society) or else {@code areaId} replaces the city range;
 * {@code standalone} keeps homes outside ({@code true}) or inside ({@code false}) a society.
 */
public record PropertySearchCriteria(
	String cityCode,
	String localityPrefix,
	/** Any of these types (OR); empty means every type. */
	List<PropertyType> types,
	Integer bedrooms,
	BigDecimal minRating,
	String text,
	Long areaId,
	Long societyId,
	Long towerId,
	Boolean standalone,
	/** {@code GET /properties?homeSize=}, any of these (OR); empty means every size (captain 2026-11-23 17:18). */
	List<HomeSize> homeSizes,
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
		types = types == null ? List.of() : List.copyOf(types);
		homeSizes = homeSizes == null ? List.of() : List.copyOf(homeSizes);
	}
	
	/** One type and one size, as before the filters took lists. */
	public PropertySearchCriteria(String cityCode, String localityPrefix, PropertyType type, Integer bedrooms,
		BigDecimal minRating, String text, Long areaId, Long societyId, Long towerId, Boolean standalone,
		HomeSize homeSize) {
		this(cityCode, localityPrefix, type == null ? List.of() : List.of(type), bedrooms, minRating, text, areaId,
			societyId, towerId, standalone, homeSize == null ? List.of() : List.of(homeSize), null);
	}
	
	public PropertySearchCriteria(String cityCode, String localityPrefix, PropertyType type, Integer bedrooms,
		BigDecimal minRating, String text, Long areaId, Long societyId, Long towerId, Boolean standalone) {
		this(cityCode, localityPrefix, type, bedrooms, minRating, text, areaId, societyId, towerId, standalone,
			(HomeSize) null);
	}
	
	/** The same filters with {@code text} resolved to {@code places}. */
	public PropertySearchCriteria withTextPlaces(TextPlaces places) {
		return new PropertySearchCriteria(cityCode, localityPrefix, types, bedrooms, minRating, text, areaId, societyId,
			towerId, standalone, homeSizes, places);
	}
	
	/** Filters without location hierarchy ids. */
	public PropertySearchCriteria(String cityCode, String localityPrefix, PropertyType type, Integer bedrooms,
		BigDecimal minRating, String text) {
		this(cityCode, localityPrefix, type, bedrooms, minRating, text, null, null, null, null, null);
	}
	
}
