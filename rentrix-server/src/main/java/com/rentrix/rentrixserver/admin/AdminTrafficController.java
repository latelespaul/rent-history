package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.metrics.TrafficService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /admin/traffic}: daily-aggregate visit counts (D-175). Admin host and admin console session only. */
@RestController
@RequiredArgsConstructor
public class AdminTrafficController {
	
	private final TrafficService traffic;
	
	@GetMapping("/admin/traffic")
	public TrafficService.Summary traffic() {
		return traffic.summary();
	}
	
}
