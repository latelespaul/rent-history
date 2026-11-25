package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.HomeSize;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.verification.VerificationState;

import java.util.UUID;

/** One search result (§12.2 {@code GET /properties}); public fields only (D-56, D-57). */
public record PropertyCard(
	UUID id,
	CityRef city,
	String locality,
	String buildingName,
	String street,
	PropertyType propertyType,
	Integer bedrooms,
	@JsonInclude(JsonInclude.Include.NON_NULL) HomeSize homeSize,
	@JsonInclude(JsonInclude.Include.NON_NULL) String homeSizeOther,
	Integer bathrooms,
	Integer areaSqft,
	Furnishing furnishing,
	Boolean parking,
	boolean demo,
	RatingSummary rating,
	/** "Owner", never the landlord's name (D-168), or "Not on Doorway Living yet" for an unclaimed home. */
	String landlordLabel,
	/**
	 * True when the area, society, tower or wing is a new name nobody has reviewed yet ({@code PENDING}). It shows
	 * at once, open by default, and the web tags it "Pending review" (captain 2026-11-23, R2 / R8).
	 */
	boolean locationPending,
	/**
	 * The home's green tick (D-134, D-148): {@code UNVERIFIED} homes are listed like any other and the web tags them
	 * "Not verified yet", never hidden (open by default, D-162).
	 */
	VerificationState verification,
	/**
	 * Where the home sits (D-107): area, and society / tower / wing when in one, for the card's title line
	 * "Society · Tower B · Flat 1204" (D-165). Building-level data, public like the property page's.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL) PropertyLocation location,
	/**
	 * The flat number, only when it is {@code OPEN} to everyone (D-140); absent for a {@code MASKED} one (D-57), so
	 * a card never shows more than the property page shows a stranger.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL) String unitLabel,
	/**
	 * Every published review the home's page lists, verified or not (D-165); {@code rating.reviewCount} counts only
	 * the verified ones behind the headline average (D-163).
	 */
	int listedReviews
) {
}
