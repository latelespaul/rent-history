package com.rentrix.rentrixserver.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The answer to {@code POST /tenancies/{id}/review-form}: the reviews it wrote (D-157). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewFormView(ReviewView home, ReviewView landlord) {
}
