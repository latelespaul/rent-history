package com.rentrix.rentrixserver.location.dto;

import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.property.dto.CityRef;

import java.util.List;
import java.util.UUID;

/**
 * The area page ({@code GET /areas/{id}}, D-112): the area, its societies and how many of its published homes are
 * standalone. The homes themselves are {@code GET /properties?area=&standalone=true} (and {@code ?society=}).
 */
public record AreaDetail(
	UUID id,
	CityRef city,
	String name,
	List<String> aliases,
	List<String> pincodes,
	LocationStatus status,
	long publishedHomes,
	long standaloneHomes,
	List<SocietyCard> societies
) {
}
