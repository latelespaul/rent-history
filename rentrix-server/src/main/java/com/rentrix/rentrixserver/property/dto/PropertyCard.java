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
	String landlordName,
	/**
	 * True when the area, society, tower or wing is a new name nobody has reviewed yet ({@code PENDING}). It shows
	 * at once, open by default, and the web tags it "Pending review" (captain 2026-11-23, R2 / R8).
	 */
	boolean locationPending,
	/**
	 * The home's green tick (D-134, D-148): {@code UNVERIFIED} homes are listed like any other and the web tags them
	 * "Not verified yet", never hidden (open by default, D-158).
	 */
	VerificationState verification
) {
}
