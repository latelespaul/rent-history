package com.rentrix.rentrixserver.location.dto;

import com.rentrix.rentrixserver.location.Area;

import java.util.UUID;

/** An area named inside another row: its {@code public_id} and name. */
public record AreaRef(UUID id, String name) {
	
	public static AreaRef of(Area area) {
		return new AreaRef(area.getPublicId(), area.getName());
	}
	
}
