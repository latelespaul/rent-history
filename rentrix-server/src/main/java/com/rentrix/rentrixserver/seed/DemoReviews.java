package com.rentrix.rentrixserver.seed;

import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewCriterion;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.review.ReviewResponse;
import com.rentrix.rentrixserver.review.ReviewResponseRepository;
import com.rentrix.rentrixserver.review.ReviewService;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.tenancy.Tenancy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Demo reviews for the dev seed (PLAN B6, §16): on each confirmed demo tenancy the tenant reviews the landlord and
 * the home and the landlord reviews the tenant. Ratings walk a fixed list so the seed shows every star from 1 to 5;
 * texts are generic and pass the D-43 content rules. Written shortly after the tenancy ended (or 100 days into a
 * current one) and published together, as the double-blind reveal would (D-46). Exception: on the current tenancy
 * of the {@code waiting} home only the landlord has written, two days ago, so that review awaits reveal and the
 * tenant has review tasks. The landlord of the first home answers its newest property review.
 * <p>The tenant's reviews are written with the review form (D-157): a rating for every parameter of the home or the
 * landlord, near the overall rating, and "would you recommend" yes from 4 stars, so every home and landlord page
 * shows per-parameter averages (D-158). The landlord's reviews of tenants keep the D-29 sub-ratings.
 */
final class DemoReviews {

	/** Overall ratings in seeding order, spread over 1–5 and mostly positive like real reviews. */
	static final int[] RATINGS = {5, 4, 4, 3, 5, 2, 4, 5, 1, 4, 3, 5, 4, 4, 2, 5, 3, 4, 5, 4, 3, 1, 4, 5};

	static final Map<ReviewDirection, List<String>> HIGH = Map.of(
		ReviewDirection.TENANT_TO_LANDLORD, List.of(
			"Answered every message the same day and fixed the geyser within a week. The full deposit came back on time.",
			"Fair about charges and never visited without asking first. Would happily rent from this landlord again.",
			"Clear agreement, no surprise fees, and repairs were handled without any argument at all."),
		ReviewDirection.LANDLORD_TO_TENANT, List.of(
			"Paid the rent on the first of every month and kept the home clean. Handed it back in good condition.",
			"Easy to deal with, told us early about every repair and looked after the furniture well.",
			"Respectful neighbour, no complaints from the society, and the flat was spotless at move out."),
		ReviewDirection.TENANT_TO_PROPERTY, List.of(
			"Bright rooms, steady water supply and almost no power cuts. The neighbourhood is calm at night.",
			"Well maintained building with a working lift and good security. Shops and buses are a short walk away.",
			"Good ventilation, no damp in the monsoon, and the kitchen fittings were all in working order."));

	static final Map<ReviewDirection, List<String>> MIXED = Map.of(
		ReviewDirection.TENANT_TO_LANDLORD, List.of(
			"Friendly but slow with repairs; the leaking tap took a month. The deposit was returned with small cuts."),
		ReviewDirection.LANDLORD_TO_TENANT, List.of(
			"Rent was sometimes a few days late but always paid. Some wall marks had to be repainted after move out."),
		ReviewDirection.TENANT_TO_PROPERTY, List.of(
			"Decent home for the rent. Water pressure drops in summer and the road outside gets noisy in the evening."));

	static final Map<ReviewDirection, List<String>> LOW = Map.of(
		ReviewDirection.TENANT_TO_LANDLORD, List.of(
			"Repairs were ignored for weeks and a large part of the deposit was kept without a clear reason."),
		ReviewDirection.LANDLORD_TO_TENANT, List.of(
			"Rent was often late by two weeks and the kitchen needed a deep clean and new fittings after move out."),
		ReviewDirection.TENANT_TO_PROPERTY, List.of(
			"Damp walls in every room during the monsoon and frequent water shortages. Not worth the rent asked."));

	static final String RESPONSE = "Thank you for the feedback. The water pump was replaced after you moved out and "
		+ "the lift is now serviced every quarter.";

	private final ReviewRepository reviews;
	private final ReviewResponseRepository responses;
	private final Clock clock;
	private int next;

	DemoReviews(ReviewRepository reviews, ReviewResponseRepository responses, Clock clock) {
		this.reviews = reviews;
		this.responses = responses;
		this.clock = clock;
	}

	/**
	 * @param confirmed the confirmed demo tenancies
	 * @param waiting   the home whose current tenancy gets only the landlord's review, awaiting reveal
	 * @return the number of reviews written
	 */
	int seed(List<Tenancy> confirmed, Property waiting) {
		int count = 0;
		Review answered = null;
		for (Tenancy tenancy : confirmed) {
			boolean current = tenancy.getEndDate() == null;
			if (current && tenancy.getProperty().getId().equals(waiting.getId())) {
				Instant submitted = clock.instant().truncatedTo(ChronoUnit.MILLIS).minus(Duration.ofDays(2));
				review(tenancy, ReviewDirection.LANDLORD_TO_TENANT, submitted, null);
				count++;
				continue;
			}
			LocalDate written = current ? tenancy.getStartDate().plusDays(100) : tenancy.getEndDate().plusDays(5);
			Instant tenantAt = written.atTime(10, 0).toInstant(ZoneOffset.UTC);
			Instant landlordAt = tenantAt.plus(Duration.ofDays(3));
			review(tenancy, ReviewDirection.TENANT_TO_LANDLORD, tenantAt, landlordAt);
			Review property = review(tenancy, ReviewDirection.TENANT_TO_PROPERTY, tenantAt, tenantAt);
			review(tenancy, ReviewDirection.LANDLORD_TO_TENANT, landlordAt, landlordAt);
			count += 3;
			if (current && answered == null) {
				answered = property;
			}
		}
		if (answered != null) {
			ReviewResponse response = new ReviewResponse();
			response.setReview(answered);
			response.setAuthor(answered.getProperty().getLandlord());
			response.setBody(RESPONSE);
			Instant at = answered.getPublishedAt().plus(Duration.ofDays(2));
			response.setCreatedAt(at);
			response.setEditUntil(at.plus(ReviewService.EDIT_WINDOW));
			responses.save(response);
		}
		return count;
	}

	/** One review; {@code publishedAt} null leaves it awaiting reveal. */
	private Review review(Tenancy tenancy, ReviewDirection direction, Instant submittedAt, Instant publishedAt) {
		int rating = RATINGS[next % RATINGS.length];
		int index = next++;
		Map<ReviewDirection, List<String>> pool = rating >= 4 ? HIGH : rating == 3 ? MIXED : LOW;
		List<String> texts = pool.get(direction);
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(direction);
		review.setAuthor(direction == ReviewDirection.LANDLORD_TO_TENANT ? tenancy.getLandlord() : tenancy.getTenant());
		review.setSubjectUser(switch (direction) {
			case TENANT_TO_LANDLORD -> tenancy.getLandlord();
			case LANDLORD_TO_TENANT -> tenancy.getTenant();
			case TENANT_TO_PROPERTY -> null;
		});
		review.setProperty(tenancy.getProperty());
		review.setRatingOverall(rating);
		if (direction == ReviewDirection.LANDLORD_TO_TENANT) {
			review.setRatingA(rating);
			review.setRatingB(Math.max(1, rating - index % 2));
			review.setRatingC(index % 3 == 0 ? null : Math.min(5, rating + index % 2));
		} else {
			List<ReviewCriterion> criteria = ReviewCriterion.of(direction);
			for (int i = 0; i < criteria.size(); i++) {
				// One star either side of the overall rating, varied per parameter and review.
				int spread = (index + i) % 3 - 1;
				review.getCriteria().put(criteria.get(i), Math.max(1, Math.min(5, rating + spread)));
			}
			review.setWouldRecommend(rating >= 4);
			review.setFormReview(true);
		}
		review.setBody(texts.get(index % texts.size()));
		review.setSubmittedAt(submittedAt);
		review.setEditUntil(submittedAt.plus(ReviewService.EDIT_WINDOW));
		review.setStatus(publishedAt == null ? ReviewStatus.AWAITING_REVEAL : ReviewStatus.PUBLISHED);
		review.setPublishedAt(publishedAt);
		review.setCreatedAt(submittedAt);
		return reviews.save(review);
	}

}
