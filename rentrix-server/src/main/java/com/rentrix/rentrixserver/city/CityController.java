package com.rentrix.rentrixserver.city;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /cities?q=}: the live cities by name, the only values city pickers and filters offer
 * (D-20, D-82). {@code q} filters by word prefix over code, name, aliases and state.
 */
@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
public class CityController {
	
	private final CityService cityService;
	
	@GetMapping
	public List<CityResponse> list(@RequestParam(required = false) String q) {
		return cityService.liveCities(q);
	}
	
}
