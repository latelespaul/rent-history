package com.rentrix.rentrixserver.property.dto;

import com.rentrix.rentrixserver.city.City;

/** City of a property: its code and display name. */
public record CityRef(String code, String name) {
	
	public static CityRef of(City city) {
		return new CityRef(city.getCode(), city.getName());
	}
	
}
