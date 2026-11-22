package com.rentrix.rentrixserver.tenancy.dto;

import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.property.dto.CityRef;

import java.util.UUID;

/** The property of a tenancy; {@code unitLabel} is shown because only parties and admins read tenancies (D-57). */
public record TenancyPropertyRef(UUID id, String buildingName, String locality, CityRef city, String unitLabel,
	PropertyType propertyType) {
}
