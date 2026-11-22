package com.rentrix.rentrixserver.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PATCH /reviews/{id}}: the new version of ratings and text; it replaces the old one, which is kept (§5.7). */
public record UpdateReviewRequest(
	@NotNull @Min(1) @Max(5) Integer ratingOverall,
	@Min(1) @Max(5) Integer ratingA,
	@Min(1) @Max(5) Integer ratingB,
	@Min(1) @Max(5) Integer ratingC,
	@NotNull @Size(max = 4000) String body
) {
}
