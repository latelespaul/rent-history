package com.rentrix.rentrixserver.review.dto;

import com.rentrix.rentrixserver.review.ReviewDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /tenancies/{id}/reviews}. Ratings are whole stars 1–5 (D-27); the sub-ratings are optional and mean
 * what D-29 says for the direction. The body's length and content are checked by the D-43 rules. The review form
 * (D-157) has its own endpoint, {@code POST /tenancies/{id}/review-form}.
 */
public record CreateReviewRequest(
	@NotNull ReviewDirection direction,
	@NotNull @Min(1) @Max(5) Integer ratingOverall,
	@Min(1) @Max(5) Integer ratingA,
	@Min(1) @Max(5) Integer ratingB,
	@Min(1) @Max(5) Integer ratingC,
	@NotNull @Size(max = 4000) String body
) {
}
