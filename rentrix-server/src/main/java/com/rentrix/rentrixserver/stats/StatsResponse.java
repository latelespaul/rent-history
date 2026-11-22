package com.rentrix.rentrixserver.stats;

import java.util.List;

/**
 * {@code GET /stats} (§12.2, D-03): real counts only. Every live city has a row, zeros included, so the web
 * never invents a number.
 */
public record StatsResponse(long publishedProperties, long confirmedTenancies, long publishedReviews,
	List<CityStats> cities) {
	
	public record CityStats(String city, long publishedProperties, long confirmedTenancies, long publishedReviews) {
	}
	
}
