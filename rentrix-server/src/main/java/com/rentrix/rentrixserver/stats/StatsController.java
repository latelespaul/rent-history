package com.rentrix.rentrixserver.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /stats}: public, real counts per live city (D-03). */
@RestController
@RequiredArgsConstructor
public class StatsController {
	
	private final StatsService stats;
	
	@GetMapping("/stats")
	public StatsResponse stats() {
		return stats.stats();
	}
	
}
