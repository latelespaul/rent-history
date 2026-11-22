package com.rentrix.rentrixserver.property;

import java.math.BigDecimal;

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
	PropertyType type,
	Integer bedrooms,
	BigDecimal minRating,
	String text,
	Long areaId,
	Long societyId,
	Long towerId,
	Boolean standalone
) {
	
	/** Filters without location hierarchy ids. */
	public PropertySearchCriteria(String cityCode, String localityPrefix, PropertyType type, Integer bedrooms,
		BigDecimal minRating, String text) {
		this(cityCode, localityPrefix, type, bedrooms, minRating, text, null, null, null, null);
	}
	
}
