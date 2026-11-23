package com.rentrix.rentrixserver.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * {@code PATCH /reviews/{id}}: the new version of ratings and text; it replaces the old one, which is kept (§5.7).
 * The form fields of D-157 ({@code criteria}, {@code wouldRecommend}, {@code pros}, {@code cons}) keep their value
 * when absent, so an editor that only knows the overall rating and text never drops them.
 */
public record UpdateReviewRequest(
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
	
	public UpdateReviewRequest(Integer ratingOverall, Integer ratingA, Integer ratingB, Integer ratingC, String body) {
		this(ratingOverall, ratingA, ratingB, ratingC, body, null, null, null, null);
	}
	
}
