package com.rentrix.rentrixserver.property.dto;

import com.rentrix.rentrixserver.location.LocationInput;
import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.HomeSize;
import com.rentrix.rentrixserver.property.PropertyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /properties} body (§12.2). {@code city} is a live city code and {@code pincode} must match it
 * (D-84). {@code location} places the home in the hierarchy (area, then optional society, tower and wing, each an
 * existing id or an inline proposal, D-109, D-110); the locality is then copied from the area and {@code locality}
 * is ignored. Without {@code location} the free-text {@code locality} of B4a is still accepted until B4b makes the
 * area required.
 */
public record CreatePropertyRequest(
	@NotBlank @Size(max = 8) String city,
	@Size(max = 120) String locality,
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
	@Size(max = 2000) String description,
	LocationInput location,
	/** Optional (captain 2026-11-23 17:18); it fills {@code bedrooms} when that is not given. */
	HomeSize homeSize,
	/** The size in words, only with {@code homeSize} {@code OTHER}. */
	@Size(max = 40) String homeSizeOther
) {
	
	/** A request without a home size (the landlord form, older callers). */
	public CreatePropertyRequest(String city, String locality, String buildingName, String street, String unitLabel,
		String pincode, PropertyType propertyType, Integer bedrooms, Integer bathrooms, Integer areaSqft,
		Furnishing furnishing, Boolean parking, String description, LocationInput location) {
		this(city, locality, buildingName, street, unitLabel, pincode, propertyType, bedrooms, bathrooms, areaSqft,
			furnishing, parking, description, location, null, null);
	}
}
