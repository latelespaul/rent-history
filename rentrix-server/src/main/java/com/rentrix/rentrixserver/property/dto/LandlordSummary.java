package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * The owner as a property page shows them. Anonymous visitors get the short name and landlord rating only
 * (D-55); {@code id}, the link to the landlord profile, is present for signed-in callers.
 */
public record LandlordSummary(
	@JsonInclude(JsonInclude.Include.NON_NULL) UUID id,
	String shortName,
	RatingSummary rating
) {
}
