package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.location.SocietyWing;

import java.util.UUID;

/**
 * Where a property sits in the hierarchy (D-107): the area always, society / tower / wing when inside a society.
 * Every level is public (building-level data); {@code status} lets the owner's form show "Pending review" on a
 * proposal (D-109).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertyLocation(Level area, Level society, Level tower, Level wing) {
	
	public record Level(UUID id, String name, LocationStatus status) {
	}
	
	public static PropertyLocation of(Area area, Society society, SocietyTower tower, SocietyWing wing) {
		if (area == null) {
			return null;
		}
		return new PropertyLocation(new Level(area.getPublicId(), area.getName(), area.getStatus()),
			society == null ? null : new Level(society.getPublicId(), society.getName(), society.getStatus()),
			tower == null ? null : new Level(tower.getPublicId(), tower.getName(), tower.getStatus()),
			wing == null ? null : new Level(wing.getPublicId(), wing.getName(), wing.getStatus()));
	}
	
}
