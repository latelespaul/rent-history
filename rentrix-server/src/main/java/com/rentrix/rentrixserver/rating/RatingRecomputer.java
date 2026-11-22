package com.rentrix.rentrixserver.rating;

import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.RatingAggregateId;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one rating recompute hook (D-33). Every change to a review that may count (publish, edit, withdraw, hide,
 * restore, supersede) calls {@link #reviewChanged(Review)} in the same transaction. It first resets the repeat-pair
 * flags of the review's group (D-44), then rebuilds the subject's stored {@code rating_aggregates} row from source
 * reviews, never by increment, so a row is always self-correcting. {@link #recomputeAll()} is the nightly repair
 * pass, which also refreshes every Bayesian score with the day's prior mean (D-53).
 * <p>Counting rules (§9): only {@code PUBLISHED} reviews with {@code counts_in_aggregate = true}. In each group,
 * the person reviews of one landlord–tenant pair in one direction, or the property reviews of one tenant about one
 * property, only the review of the most recent tenancy counts; older ones stay visible but stop counting.
 * <p>PLAN B16 adds the {@code SOCIETY}, {@code AREA} and {@code OWNER_HOMES} roll-ups in
 * {@link #recomputeSubjects(Review)} (after the property row) and in {@link #recomputeAll()}.
 */
@Component
@RequiredArgsConstructor
public class RatingRecomputer {

	/** D-53: weight {@code C} of the prior in the Bayesian average. */
	public static final int PRIOR_WEIGHT = 5;
	/** D-53: the prior mean {@code m} until a subject type has {@value #PRIOR_MIN_REVIEWS} counted reviews. */
	public static final BigDecimal DEFAULT_PRIOR = new BigDecimal("3.5");
	public static final int PRIOR_MIN_REVIEWS = 50;

	/** The most recent tenancy first: start date, then creation order. */
	private static final Comparator<Review> NEWEST_TENANCY_FIRST = Comparator
		.comparing((Review r) -> r.getTenancy().getStartDate()).reversed()
		.thenComparing(Comparator.comparing((Review r) -> r.getTenancy().getId()).reversed());

	private final RatingSourceRepository source;
	private final RatingAggregateRepository aggregates;

	/** The hook: call after a review's status, ratings or counting changed, inside the same transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void reviewChanged(Review review) {
		markCounting(group(review));
		recomputeSubjects(review);
	}

	/** Rebuilds the row of one subject from its counted reviews and returns it. */
	@Transactional(propagation = Propagation.MANDATORY)
	public RatingAggregate recompute(RatingSubjectType type, Long subjectId) {
		RatingAggregate row = totals(type, subjectId);
		aggregates.saveAndFlush(row);
		row.setBayesScore(bayes(row, prior(type)));
		return aggregates.saveAndFlush(row);
	}

	/**
	 * Nightly repair (D-33, D-53): resets every repeat-pair flag, rebuilds every row from source reviews (rows whose
	 * reviews are all gone drop to zero), then recomputes each type's prior mean and every Bayesian score with it.
	 *
	 * @return the number of rows rebuilt
	 */
	@Transactional
	public int recomputeAll() {
		List<Review> all = source.allWithTenancy();
		Map<String, List<Review>> groups = all.stream().collect(Collectors.groupingBy(RatingRecomputer::groupKey));
		groups.values().forEach(this::markCounting);
		int rows = 0;
		for (RatingSubjectType type : RatingSubjectType.values()) {
			Set<Long> subjects = new LinkedHashSet<>(subjectsWithReviews(type));
			subjects.addAll(source.storedSubjects(type));
			List<RatingAggregate> rebuilt = subjects.stream().map(id -> totals(type, id)).toList();
			aggregates.saveAllAndFlush(rebuilt);
			BigDecimal prior = prior(type);
			rebuilt.forEach(row -> row.setBayesScore(bayes(row, prior)));
			aggregates.saveAllAndFlush(rebuilt);
			rows += rebuilt.size();
		}
		return rows;
	}

	/** The subject rows a review feeds. */
	private void recomputeSubjects(Review review) {
		switch (review.getDirection()) {
			case TENANT_TO_LANDLORD -> recompute(RatingSubjectType.LANDLORD, review.getSubjectUser().getId());
			case LANDLORD_TO_TENANT -> recompute(RatingSubjectType.TENANT, review.getSubjectUser().getId());
			case TENANT_TO_PROPERTY -> recompute(RatingSubjectType.PROPERTY, review.getProperty().getId());
		}
	}

	private List<Review> group(Review review) {
		Tenancy tenancy = review.getTenancy();
		return review.getDirection() == ReviewDirection.TENANT_TO_PROPERTY
			? source.propertyGroup(review.getDirection(), review.getProperty().getId(), review.getAuthor().getId())
			: source.pairGroup(review.getDirection(), tenancy.getLandlord().getId(), tenancy.getTenant().getId());
	}

	private static String groupKey(Review r) {
		return r.getDirection() == ReviewDirection.TENANT_TO_PROPERTY
			? r.getDirection() + ":" + r.getProperty().getId() + ":" + r.getAuthor().getId()
			: r.getDirection() + ":" + r.getTenancy().getLandlord().getId() + ":" + r.getTenancy().getTenant().getId();
	}

	/**
	 * D-44: of a group's published reviews only the one of the most recent tenancy counts. Reviews that are not
	 * published (awaiting reveal, hidden, withdrawn) never count, and an unpublished newer review does not
	 * supersede an older published one, so a subject cannot learn of a hidden review from their rating.
	 */
	private void markCounting(List<Review> group) {
		Review counted = group.stream()
			.filter(r -> r.getStatus() == ReviewStatus.PUBLISHED)
			.min(NEWEST_TENANCY_FIRST)
			.orElse(null);
		for (Review r : group) {
			boolean counts = r == counted;
			if (r.isCountsInAggregate() != counts) {
				r.setCountsInAggregate(counts);
			}
		}
	}

	private List<Long> subjectsWithReviews(RatingSubjectType type) {
		return switch (type) {
			case LANDLORD -> source.personSubjects(ReviewDirection.TENANT_TO_LANDLORD);
			case TENANT -> source.personSubjects(ReviewDirection.LANDLORD_TO_TENANT);
			case PROPERTY -> source.propertySubjects(ReviewDirection.TENANT_TO_PROPERTY);
		};
	}

	/** The subject's row with its counts rebuilt from source; the Bayesian score is set afterwards. */
	private RatingAggregate totals(RatingSubjectType type, Long subjectId) {
		List<Object[]> result = switch (type) {
			case LANDLORD -> source.personTotals(subjectId, ReviewDirection.TENANT_TO_LANDLORD, ReviewStatus.PUBLISHED);
			case TENANT -> source.personTotals(subjectId, ReviewDirection.LANDLORD_TO_TENANT, ReviewStatus.PUBLISHED);
			case PROPERTY -> source.propertyTotals(subjectId, ReviewDirection.TENANT_TO_PROPERTY, ReviewStatus.PUBLISHED);
		};
		Object[] t = result.get(0);
		RatingAggregate row = aggregates.findById(new RatingAggregateId(type, subjectId))
			.orElseGet(() -> new RatingAggregate(type, subjectId));
		int n = intOf(t[0]);
		int sum = intOf(t[1]);
		row.setReviewCount(n);
		row.setRatingSum(sum);
		row.setAverage(n == 0 ? null : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP));
		row.setCount1(intOf(t[2]));
		row.setCount2(intOf(t[3]));
		row.setCount3(intOf(t[4]));
		row.setCount4(intOf(t[5]));
		row.setCount5(intOf(t[6]));
		row.setSubAAvg(decimalOf(t[7]));
		row.setSubBAvg(decimalOf(t[8]));
		row.setSubCAvg(decimalOf(t[9]));
		row.setLastReviewAt((Instant) t[10]);
		row.setBayesScore(null);
		return row;
	}

	/**
	 * D-53: the mean of all counted reviews of the subject type, or {@link #DEFAULT_PRIOR} below
	 * {@value #PRIOR_MIN_REVIEWS} of them. Read from the stored rows, so the nightly pass gives every row the
	 * same prior; a single recompute uses the prior as it stands at that moment.
	 */
	BigDecimal prior(RatingSubjectType type) {
		Object[] t = source.typeTotals(type).get(0);
		long n = ((Number) t[0]).longValue();
		long sum = ((Number) t[1]).longValue();
		if (n < PRIOR_MIN_REVIEWS) {
			return DEFAULT_PRIOR;
		}
		return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
	}

	/** D-53: {@code (C·m + rating_sum) / (C + n)}, three decimals; none for a subject with no counted review. */
	static BigDecimal bayes(RatingAggregate row, BigDecimal prior) {
		if (row.getReviewCount() == 0) {
			return null;
		}
		BigDecimal numerator = prior.multiply(BigDecimal.valueOf(PRIOR_WEIGHT)).add(BigDecimal.valueOf(row.getRatingSum()));
		return numerator.divide(BigDecimal.valueOf((long) PRIOR_WEIGHT + row.getReviewCount()), 3, RoundingMode.HALF_UP);
	}

	private static int intOf(Object value) {
		return value == null ? 0 : ((Number) value).intValue();
	}

	private static BigDecimal decimalOf(Object value) {
		return value == null ? null : BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
	}
	
}
