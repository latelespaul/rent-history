package com.rentrix.rentrixserver.location.dto;

import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.SocietyKind;
import com.rentrix.rentrixserver.property.dto.CityRef;

import java.util.UUID;

/** One society in {@code GET /societies} and on the area page (D-112); rating figures come with B16. */
public record SocietyCard(
	UUID id,
	String name,
	String developer,
	SocietyKind kind,
	AreaRef area,
	CityRef city,
	String street,
	LocationStatus status,
	long towerCount,
	long publishedHomes
) {
}
