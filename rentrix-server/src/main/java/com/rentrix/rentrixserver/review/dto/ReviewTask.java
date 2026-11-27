package com.rentrix.rentrixserver.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.tenancy.dto.PartyRef;

import java.util.UUID;

/**
 * A review the caller can write now ({@code GET /me/review-tasks}): an own confirmed tenancy whose window is open
 * (D-39) and has no review of the caller in this direction yet. {@code counterparty} is the person to review
 * (absent for a property review). {@code otherSideWaiting} is true on the owner's task when the tenant already
 * submitted their review of the owner and it waits for this one to be revealed (D-46). It is always false on a
 * tenant's task: a tenant never learns of the owner's review of them (D-175).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewTask(
	UUID tenancyId,
	ReviewDirection direction,
	ReviewView.PropertyRef property,
	PartyRef counterparty,
	ReviewView.Period period,
	boolean otherSideWaiting
) {
}
