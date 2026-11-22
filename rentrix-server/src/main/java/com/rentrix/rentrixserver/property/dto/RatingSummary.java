package com.rentrix.rentrixserver.property.dto;

import com.rentrix.rentrixserver.property.PropertySearchQuery;
import com.rentrix.rentrixserver.review.RatingAggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A stored aggregate as cards and pages show it (§9): the count always, the average (one decimal) only from
 * {@value PropertySearchQuery#MIN_REVIEWS_FOR_AVERAGE} reviews on (D-52), so the UI shows "New · n reviews" below.
 */
public record RatingSummary(int reviewCount, BigDecimal average) {
	
	public static final RatingSummary NONE = new RatingSummary(0, null);
	
	public static RatingSummary of(RatingAggregate aggregate) {
		if (aggregate == null) {
			return NONE;
		}
		BigDecimal average = aggregate.getReviewCount() >= PropertySearchQuery.MIN_REVIEWS_FOR_AVERAGE
			&& aggregate.getAverage() != null ? aggregate.getAverage().setScale(1, RoundingMode.HALF_UP) : null;
		return new RatingSummary(aggregate.getReviewCount(), average);
	}
	
}
