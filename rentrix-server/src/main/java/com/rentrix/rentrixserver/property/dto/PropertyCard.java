package com.rentrix.rentrixserver.property.dto;

import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.PropertyType;

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
	Integer bathrooms,
	Integer areaSqft,
	Furnishing furnishing,
	Boolean parking,
	boolean demo,
	RatingSummary rating,
	String landlordName
) {
}
