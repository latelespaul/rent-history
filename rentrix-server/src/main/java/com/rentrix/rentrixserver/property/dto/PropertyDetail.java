package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.verification.VerificationState;

import java.time.Instant;
import java.util.UUID;

/**
 * A property page ({@code GET /properties/{id}}), and the owner's view of their own listings. Fields follow
 * D-56 and D-57: {@code unitLabel} (flat or house number) is present only for the owner and admins.
 * {@code location} is the place in the hierarchy (D-107); absent for a B4a home placed by free-text locality only.
 * {@code addedByTenant} marks a home a tenant added while its landlord is not on the platform (D-138), shown "Added
 * by a tenant · not verified yet". {@code verification} is the green tick (D-134). {@code ownerContact} is what that
 * tenant entered about the owner: private, present only for them and admins (D-139). Where
 * {@code unitLabelPublic} is true (flat number {@code OPEN}, D-139), the flat number is shown to everyone who may
 * see the home.
 */
public record PropertyDetail(
	UUID id,
	CityRef city,
	String locality,
	String buildingName,
	String street,
	String pincode,
	@JsonInclude(JsonInclude.Include.NON_NULL) String unitLabel,
	boolean unitLabelPublic,
	PropertyType propertyType,
	Integer bedrooms,
	Integer bathrooms,
	Integer areaSqft,
	Furnishing furnishing,
	Boolean parking,
	String description,
	PropertyStatus status,
	boolean demo,
	RatingSummary rating,
	LandlordSummary landlord,
	Instant createdAt,
	Instant updatedAt,
	@JsonInclude(JsonInclude.Include.NON_NULL) PropertyLocation location,
	boolean addedByTenant,
	VerificationState verification,
	@JsonInclude(JsonInclude.Include.NON_NULL) OwnerContact ownerContact
) {
	
	/** This page with the owner contact a tenant entered (D-139), for that tenant and admins only. */
	public PropertyDetail withOwnerContact(OwnerContact contact) {
		return new PropertyDetail(id, city, locality, buildingName, street, pincode, unitLabel, unitLabelPublic, propertyType, bedrooms,
			bathrooms, areaSqft, furnishing, parking, description, status, demo, rating, landlord, createdAt, updatedAt,
			location, addedByTenant, verification, contact);
	}
	
}
