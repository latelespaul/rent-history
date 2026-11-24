package com.rentrix.rentrixserver.review.dto;

import java.util.Map;

/**
 * {@code POST /tenancies/{id}/review-form} (D-157): the tenant's review form, the home part and, when the landlord can
 * be reviewed, the landlord part, saved together or not at all. Every field is checked by the service, which answers
 * one {@code 422 validation_failed} with a field error per broken rule ({@code home.criteria.PARKING},
 * {@code landlord.ratingOverall}, {@code home.pros}).
 */
public record ReviewFormRequest(Part home, Part landlord) {
	
	/**
	 * One review of the form. {@code ratingOverall} is required (1–5); every parameter rating is optional (1–5);
	 * {@code body} is the part's optional comment.
	 */
	public record Part(Integer ratingOverall, Map<String, Integer> criteria, Boolean wouldRecommend, String body,
		String pros, String cons) {
	}
	
}
