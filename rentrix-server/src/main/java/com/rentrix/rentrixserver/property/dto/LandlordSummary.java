package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.identity.TrustBadges;

import java.util.UUID;

/**
 * The owner as a property page shows them (D-168): {@code label} "Owner" (or "Not on Doorway Living yet" for an
 * unclaimed home), never the name; {@code shortName} only for the owner and admins. Anonymous visitors get the label
 * and landlord rating only (D-55); {@code id}, the link to the landlord profile, is present for signed-in callers.
 * {@code badges} are the owner's three verification badges; an unclaimed home's owner has none.
 */
public record LandlordSummary(
	@JsonInclude(JsonInclude.Include.NON_NULL) UUID id,
	String label,
	@JsonInclude(JsonInclude.Include.NON_NULL) String shortName,
	RatingSummary rating,
	TrustBadges badges
) {
}
