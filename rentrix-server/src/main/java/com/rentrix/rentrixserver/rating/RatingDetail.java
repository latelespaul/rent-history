package com.rentrix.rentrixserver.rating;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.ReviewCriterion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * A stored aggregate as a rating card shows it (§9): the count always; the one-decimal average, the 1–5
 * distribution and the sub-rating averages (D-29) only from {@value #MIN_REVIEWS_FOR_AVERAGE} counted reviews
 * on (D-52), so the UI shows "New · n reviews" below that and one review never defines anyone.
 * {@code distribution} lists the counts of 1 to 5 stars in that order. On a home or landlord page (D-158) it also
 * carries {@code criteria}, the review form's parameters with their counts and, from 3 ratings each, their averages,
 * and {@code recommend}, the "would you recommend" answers ({@link RatingBreakdowns}). {@code lastReviewYear} is the
 * year of the latest counted review, never its month, day or time (D-169, D-171): the month of a current tenant's
 * review could tell the reviewed party who wrote it, and the aggregate does not know whose stay it was.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RatingDetail(int reviewCount, BigDecimal average, List<Integer> distribution, SubRatings subRatings,
	Integer lastReviewYear, List<CriterionRating> criteria, Recommend recommend) {
	
	public static final int MIN_REVIEWS_FOR_AVERAGE = 3;
	public static final RatingDetail NONE = new RatingDetail(0, null, null, null, null);
	
	public RatingDetail(int reviewCount, BigDecimal average, List<Integer> distribution, SubRatings subRatings,
		Integer lastReviewYear) {
		this(reviewCount, average, distribution, subRatings, lastReviewYear, null, null);
	}
	
	/** One form parameter (D-158): {@code average} only from {@value #MIN_REVIEWS_FOR_AVERAGE} ratings (D-52). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CriterionRating(ReviewCriterion criterion, int ratingCount, BigDecimal average) {
	}
	
	/** "Would you recommend" (D-158): how many answered and how many said yes. */
	public record Recommend(int answered, int yes) {
	}
	
	/** This rating with the form's per-parameter figures. */
	public RatingDetail withBreakdown(List<CriterionRating> criteria, Recommend recommend) {
		return new RatingDetail(reviewCount, average, distribution, subRatings, lastReviewYear, criteria, recommend);
	}
	
	/** Averages of the optional sub-ratings a, b and c; their meaning depends on the direction (D-29). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SubRatings(BigDecimal a, BigDecimal b, BigDecimal c) {
	}
	
	public static RatingDetail of(RatingAggregate row) {
		if (row == null || row.getReviewCount() == 0) {
			return NONE;
		}
		if (row.getReviewCount() < MIN_REVIEWS_FOR_AVERAGE) {
			return new RatingDetail(row.getReviewCount(), null, null, null, year(row.getLastReviewAt()));
		}
		return new RatingDetail(row.getReviewCount(), shown(row.getAverage()),
			List.of(row.getCount1(), row.getCount2(), row.getCount3(), row.getCount4(), row.getCount5()),
			new SubRatings(shown(row.getSubAAvg()), shown(row.getSubBAvg()), shown(row.getSubCAvg())),
			year(row.getLastReviewAt()));
	}
	
	private static Integer year(Instant at) {
		return at == null ? null : at.atZone(ZoneOffset.UTC).getYear();
	}
	
	static BigDecimal shown(BigDecimal value) {
		return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
	}
	
}
