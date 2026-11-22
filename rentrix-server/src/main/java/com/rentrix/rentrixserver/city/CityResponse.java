package com.rentrix.rentrixserver.city;

import java.util.List;

/** A live city as the API shows it. {@code region} is null unless the city belongs to a group such as Delhi NCR. */
public record CityResponse(String code, String name, List<String> aliases, String state, String region) {
	
	static CityResponse from(City city) {
		return new CityResponse(city.getCode(), city.getName(), city.aliasList(), city.getState(), city.getRegion());
	}
	
}
