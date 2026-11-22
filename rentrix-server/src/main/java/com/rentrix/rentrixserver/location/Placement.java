package com.rentrix.rentrixserver.location;

/**
 * A checked place for one property (D-110): the area always, society / tower / wing when the home is inside a
 * society. Rows proposed by {@link LocationService#resolve} are already saved as {@code PENDING}.
 */
public record Placement(Area area, Society society, SocietyTower tower, SocietyWing wing) {
	
	/** A standalone house or building: an area and no society. */
	public boolean standalone() {
		return society == null;
	}
	
}
