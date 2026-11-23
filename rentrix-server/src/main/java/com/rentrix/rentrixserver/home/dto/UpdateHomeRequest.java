package com.rentrix.rentrixserver.home.dto;

import com.rentrix.rentrixserver.location.LocationInput;
import com.rentrix.rentrixserver.property.HomeSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /me/homes/{id}} (D-156): the corrected address and size of a home the caller added. The whole set
 * replaces the old values (a blank text clears it). {@code location} moves the home to another area, society, tower or
 * wing with the add-home rules (an existing id or a typed name, D-109, D-139); left out, the home keeps its place and
 * the PIN code is checked against it. The PIN code must fit the city (D-84); one its area does not list is accepted, as
 * when the home was added.
 */
public record UpdateHomeRequest(
	@Size(max = 160) String buildingName,
	@Size(max = 200) String street,
	@Size(max = 40) String unitLabel,
	@NotBlank String pincode,
	HomeSize homeSize,
	@Size(max = 40) String homeSizeOther,
	LocationInput location
) {
}
