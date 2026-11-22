package com.rentrix.rentrixserver.property;

import java.math.BigDecimal;

/**
 * Validated filters of {@code GET /properties} (§12.2). Every field is optional; a null city means every live
 * city. {@code localityPrefix} and {@code text} are already normalised like {@link Property#localityKey}.
 */
public record PropertySearchCriteria(
	String cityCode,
	String localityPrefix,
	PropertyType type,
	Integer bedrooms,
	BigDecimal minRating,
	String text
) {
}
