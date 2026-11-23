package com.rentrix.rentrixserver.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /reviews/{id}}: the new version of ratings and text; it replaces the old one, which is kept (§5.7).
 * The form fields of a form review (D-157: parameter ratings, "would you recommend", pros, cons) keep their value; its
 * body is an optional comment.
 */
public record UpdateReviewRequest(
	@NotNull @Min(1) @Max(5) Integer ratingOverall,
	@Min(1) @Max(5) Integer ratingA,
	@Min(1) @Max(5) Integer ratingB,
	@Min(1) @Max(5) Integer ratingC,
	@Size(max = 4000) String body
) {
}
