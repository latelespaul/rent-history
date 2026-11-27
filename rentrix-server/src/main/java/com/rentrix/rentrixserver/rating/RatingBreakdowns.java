package com.rentrix.rentrixserver.rating;

import com.rentrix.rentrixserver.review.ReviewCriterion;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-parameter averages of the review form (D-158), for a home page, a landlord page and a tenant profile. Computed
 * at read time over one subject's counted reviews, the same ones as its headline rating (§9: published, counting, and green-tick only in
 * the {@code verified} headline mode, D-135), so nothing stored has to be kept in step (D-33). Every parameter of the
 * subject's direction is listed in form order with its rating count; its average comes from
 * {@value RatingDetail#MIN_REVIEWS_FOR_AVERAGE} ratings on (D-52), and so does the "would you recommend" share,
 * which the web works out from the answer counts.
 */
@Component
@RequiredArgsConstructor
public class RatingBreakdowns {
	
	private final RatingSourceRepository source;
	private final RatingProperties properties;
	
	/** The home's nine parameters (TENANT_TO_PROPERTY). */
	public RatingDetail forProperty(RatingDetail rating, Long propertyId) {
		ReviewDirection direction = ReviewDirection.TENANT_TO_PROPERTY;
		return rating.withBreakdown(
			criteria(direction, source.propertyCriteria(propertyId, direction, ReviewStatus.PUBLISHED,
				properties.verifiedOnly())),
			recommend(source.propertyRecommend(propertyId, direction, ReviewStatus.PUBLISHED, properties.verifiedOnly())));
	}
	
	/** The landlord's five parameters (TENANT_TO_LANDLORD). */
	public RatingDetail forLandlord(RatingDetail rating, Long userId) {
		ReviewDirection direction = ReviewDirection.TENANT_TO_LANDLORD;
		return rating.withBreakdown(
			criteria(direction, source.personCriteria(userId, direction, ReviewStatus.PUBLISHED,
				properties.verifiedOnly())),
			recommend(source.personRecommend(userId, direction, ReviewStatus.PUBLISHED, properties.verifiedOnly())));
	}
	
	/**
	 * The tenant's five parameters (LANDLORD_TO_TENANT, D-170). Only for a viewer who may read the owners' reviews of
	 * the tenant (D-175).
	 */
	public RatingDetail forTenant(RatingDetail rating, Long userId) {
		ReviewDirection direction = ReviewDirection.LANDLORD_TO_TENANT;
		return rating.withBreakdown(
			criteria(direction, source.personCriteria(userId, direction, ReviewStatus.PUBLISHED,
				properties.verifiedOnly())),
			recommend(source.personRecommend(userId, direction, ReviewStatus.PUBLISHED, properties.verifiedOnly())));
	}
	
	private static List<RatingDetail.CriterionRating> criteria(ReviewDirection direction, List<Object[]> rows) {
		Map<ReviewCriterion, long[]> totals = new EnumMap<>(ReviewCriterion.class);
		for (Object[] row : rows) {
			totals.put((ReviewCriterion) row[0], new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
		}
		return ReviewCriterion.of(direction).stream().map(criterion -> {
			long[] t = totals.getOrDefault(criterion, new long[] {0, 0});
			BigDecimal average = t[0] < RatingDetail.MIN_REVIEWS_FOR_AVERAGE ? null
				: BigDecimal.valueOf(t[1]).divide(BigDecimal.valueOf(t[0]), 1, RoundingMode.HALF_UP);
			return new RatingDetail.CriterionRating(criterion, (int) t[0], average);
		}).toList();
	}
	
	private static RatingDetail.Recommend recommend(List<Object[]> rows) {
		Object[] row = rows.get(0);
		return new RatingDetail.Recommend(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
	}
	
}
