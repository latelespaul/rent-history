package com.rentrix.rentrixserver.property.dto;

import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.PropertyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /properties} body (§12.2). {@code city} is a live city code and {@code pincode} must match it
 * (D-84). Until the location hierarchy lands (PLAN B15) {@code locality} is free text; B15 replaces it with an
 * area and copies the locality from the area (D-110).
 */
public record CreatePropertyRequest(
	@NotBlank @Size(max = 8) String city,
	@NotBlank @Size(min = 2, max = 120) String locality,
	@Size(max = 160) String buildingName,
	@Size(max = 200) String street,
	@Size(max = 40) String unitLabel,
	@NotBlank String pincode,
	@NotNull PropertyType propertyType,
	@Min(0) @Max(PropertyLimits.MAX_ROOMS) Integer bedrooms,
	@Min(0) @Max(PropertyLimits.MAX_ROOMS) Integer bathrooms,
	@Min(1) @Max(PropertyLimits.MAX_AREA_SQFT) Integer areaSqft,
	Furnishing furnishing,
	Boolean parking,
	@Size(max = 2000) String description
) {
}
