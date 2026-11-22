package com.rentrix.rentrixserver.property.dto;

/** Upper bounds for property facts, shared by requests and search filters. */
public final class PropertyLimits {
	
	public static final int MAX_ROOMS = 20;
	public static final int MAX_AREA_SQFT = 100_000;
	
	private PropertyLimits() {
	}
	
}
