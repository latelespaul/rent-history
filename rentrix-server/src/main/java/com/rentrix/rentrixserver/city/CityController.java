package com.rentrix.rentrixserver.city;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code GET /cities}: the live cities, the only values city pickers and filters offer (D-20). */
@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
public class CityController {
	
	private final CityService cityService;
	
	@GetMapping
	public List<CityResponse> list() {
		return cityService.liveCities();
	}
	
}
