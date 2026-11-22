package com.rentrix.rentrixserver.location;

import java.util.UUID;

/**
 * Where a property goes (§12.2 {@code POST /properties} {@code location}, D-109, D-110): at each level either the
 * {@code public_id} of an existing row or an inline proposal, never both. The area is required; society, tower and
 * wing are optional (all absent = standalone house or building), and each needs the level above it.
 */
public record LocationInput(
	UUID areaId,
	NewArea newArea,
	UUID societyId,
	NewSociety newSociety,
	UUID towerId,
	NewTower newTower,
	UUID wingId,
	NewWing newWing
) {
	
	/** "Suggest an area": the locality name and one of its PIN codes. */
	public record NewArea(String name, String pincode) {
	}
	
	/** "Add a society"; {@code developer} and {@code street} are optional. */
	public record NewSociety(String name, SocietyKind kind, String developer, String street) {
	}
	
	/** "Add a tower"; {@code kind} defaults to {@code TOWER}. */
	public record NewTower(String name, TowerKind kind) {
	}
	
	/** "Add a wing". */
	public record NewWing(String name) {
	}
	
	public static LocationInput of(UUID areaId, UUID societyId, UUID towerId, UUID wingId) {
		return new LocationInput(areaId, null, societyId, null, towerId, null, wingId, null);
	}
	
}
