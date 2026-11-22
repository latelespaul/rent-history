package com.rentrix.rentrixserver.rating;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.review.RatingAggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * A stored aggregate as a rating card shows it (§9): the count always; the one-decimal average, the 1–5
 * distribution and the sub-rating averages (D-29) only from {@value #MIN_REVIEWS_FOR_AVERAGE} counted reviews
 * on (D-52), so the UI shows "New · n reviews" below that and one review never defines anyone.
 * {@code distribution} lists the counts of 1 to 5 stars in that order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RatingDetail(int reviewCount, BigDecimal average, List<Integer> distribution, SubRatings subRatings,
	Instant lastReviewAt) {
	
	public static final int MIN_REVIEWS_FOR_AVERAGE = 3;
	public static final RatingDetail NONE = new RatingDetail(0, null, null, null, null);
	
	/** Averages of the optional sub-ratings a, b and c; their meaning depends on the direction (D-29). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SubRatings(BigDecimal a, BigDecimal b, BigDecimal c) {
	}
	
	public static RatingDetail of(RatingAggregate row) {
		if (row == null || row.getReviewCount() == 0) {
			return NONE;
		}
		if (row.getReviewCount() < MIN_REVIEWS_FOR_AVERAGE) {
			return new RatingDetail(row.getReviewCount(), null, null, null, row.getLastReviewAt());
		}
		return new RatingDetail(row.getReviewCount(), shown(row.getAverage()),
			List.of(row.getCount1(), row.getCount2(), row.getCount3(), row.getCount4(), row.getCount5()),
			new SubRatings(shown(row.getSubAAvg()), shown(row.getSubBAvg()), shown(row.getSubCAvg())),
			row.getLastReviewAt());
	}
	
	private static BigDecimal shown(BigDecimal value) {
		return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
	}
	
}
