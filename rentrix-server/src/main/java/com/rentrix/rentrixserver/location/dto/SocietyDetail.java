package com.rentrix.rentrixserver.location.dto;

import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.SocietyKind;
import com.rentrix.rentrixserver.property.dto.CityRef;

import java.util.List;
import java.util.UUID;

/**
 * The society page ({@code GET /societies/{id}}, D-112): towers → wings with published-home counts. The homes are
 * {@code GET /properties?society=&tower=}; unit labels never appear (D-57).
 */
public record SocietyDetail(
	UUID id,
	String name,
	String developer,
	SocietyKind kind,
	AreaRef area,
	CityRef city,
	String street,
	LocationStatus status,
	long publishedHomes,
	List<TowerView> towers
) {
}
