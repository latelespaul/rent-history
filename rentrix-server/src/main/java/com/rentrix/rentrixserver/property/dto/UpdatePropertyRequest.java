package com.rentrix.rentrixserver.property.dto;

import com.rentrix.rentrixserver.location.LocationInput;
import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /properties/{id}} body: a field left out (or null) keeps its value; an empty string clears an
 * optional text field. Location fields ({@code city}, {@code locality}, {@code buildingName}, {@code street},
 * {@code pincode}) change only in {@code DRAFT} or {@code REJECTED} (D-92). {@code status} accepts only
 * {@code ARCHIVED}, from {@code PUBLISHED} (D-21). {@code location} moves the home in the hierarchy (same rules
 * and lock as on create, D-110); the locality follows the new area.
 */
public record UpdatePropertyRequest(
	@Size(max = 8) String city,
	@Size(max = 120) String locality,
	@Size(max = 160) String buildingName,
	@Size(max = 200) String street,
	@Size(max = 40) String unitLabel,
	String pincode,
	PropertyType propertyType,
	@Min(0) @Max(PropertyLimits.MAX_ROOMS) Integer bedrooms,
	@Min(0) @Max(PropertyLimits.MAX_ROOMS) Integer bathrooms,
	@Min(1) @Max(PropertyLimits.MAX_AREA_SQFT) Integer areaSqft,
	Furnishing furnishing,
	Boolean parking,
	@Size(max = 2000) String description,
	PropertyStatus status,
	LocationInput location
) {
}
