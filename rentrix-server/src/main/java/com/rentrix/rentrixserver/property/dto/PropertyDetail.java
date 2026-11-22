package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyType;

import java.time.Instant;
import java.util.UUID;

/**
 * A property page ({@code GET /properties/{id}}), and the owner's view of their own listings. Fields follow
 * D-56 and D-57: {@code unitLabel} (flat or house number) is present only for the owner and admins.
 * {@code location} is the place in the hierarchy (D-107); absent for a B4a home placed by free-text locality only.
 */
public record PropertyDetail(
	UUID id,
	CityRef city,
	String locality,
	String buildingName,
	String street,
	String pincode,
	@JsonInclude(JsonInclude.Include.NON_NULL) String unitLabel,
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
	@JsonInclude(JsonInclude.Include.NON_NULL) PropertyLocation location
) {
}
