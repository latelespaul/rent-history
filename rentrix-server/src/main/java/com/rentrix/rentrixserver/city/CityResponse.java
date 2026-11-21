package com.rentrix.rentrixserver.city;

import java.util.List;

/** A live city as the API shows it. */
public record CityResponse(String code, String name, List<String> aliases, String state) {
	
	static CityResponse from(City city) {
		return new CityResponse(city.getCode(), city.getName(), city.aliasList(), city.getState());
	}
	
}
