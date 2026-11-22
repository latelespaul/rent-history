package com.rentrix.rentrixserver.location.dto;

import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.property.dto.CityRef;

import java.util.List;
import java.util.UUID;

/**
 * One area in {@code GET /areas} (D-108, D-112): name, PIN codes and counts. {@code status} is {@code ACTIVE}
 * except for the caller's own proposals ({@code PENDING}, D-109). Rating figures come with B16.
 */
public record AreaCard(
	UUID id,
	CityRef city,
	String name,
	List<String> aliases,
	List<String> pincodes,
	LocationStatus status,
	long societyCount,
	long publishedHomes
) {
}
