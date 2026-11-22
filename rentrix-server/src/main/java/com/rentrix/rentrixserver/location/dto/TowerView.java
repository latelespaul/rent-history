package com.rentrix.rentrixserver.location.dto;

import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.TowerKind;

import java.util.List;
import java.util.UUID;

/**
 * A tower of a society with its wings. {@code publishedHomes} counts every published home of the tower,
 * including those not in any wing.
 */
public record TowerView(
	UUID id,
	String name,
	TowerKind kind,
	LocationStatus status,
	long publishedHomes,
	List<WingView> wings
) {
	
	public record WingView(UUID id, String name, LocationStatus status, long publishedHomes) {
	}
	
}
