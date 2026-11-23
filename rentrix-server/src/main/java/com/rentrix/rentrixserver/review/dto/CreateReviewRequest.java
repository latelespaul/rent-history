package com.rentrix.rentrixserver.review.dto;

import com.rentrix.rentrixserver.review.ReviewDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * {@code POST /tenancies/{id}/reviews}. Ratings are whole stars 1–5 (D-27); the sub-ratings are optional and mean
 * what D-29 says for the direction. {@code criteria} are the form's parameter ratings (D-157), keyed by
 * {@code ReviewCriterion} name and checked by the service so each bad one gets its own field error. With at least one
 * of them the body is an optional comment; without, it is the D-43 review text. Text is checked by the D-43 rules.
 */
public record CreateReviewRequest(
	@NotNull ReviewDirection direction,
	@NotNull @Min(1) @Max(5) Integer ratingOverall,
	@Min(1) @Max(5) Integer ratingA,
	@Min(1) @Max(5) Integer ratingB,
	@Min(1) @Max(5) Integer ratingC,
	@Size(max = 4000) String body,
	Map<String, Integer> criteria,
	Boolean wouldRecommend,
	@Size(max = 4000) String pros,
	@Size(max = 4000) String cons
) {
	
	public CreateReviewRequest(ReviewDirection direction, Integer ratingOverall, Integer ratingA, Integer ratingB,
		Integer ratingC, String body) {
		this(direction, ratingOverall, ratingA, ratingB, ratingC, body, null, null, null, null);
	}
	
}
