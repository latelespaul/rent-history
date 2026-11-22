package com.rentrix.rentrixserver.stats;

import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Counts per live city (D-03, D-85). Published properties are the searchable ones; confirmed tenancies are
 * {@code ACTIVE} or {@code ENDED} (both parties confirmed, §6); published reviews are visible ones (§7.2).
 * Each count walks one city's rows through {@code ix_properties_city_status_locality} and the
 * per-property tenancy and review indexes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {
	
	private static final String SQL = """
		SELECT c.code,
		  (SELECT COUNT(*) FROM properties p
		     WHERE p.city_code = c.code AND p.status = ? AND p.deleted_at IS NULL),
		  (SELECT COUNT(*) FROM tenancies t JOIN properties p ON p.id = t.property_id
		     WHERE p.city_code = c.code AND t.status IN (?, ?)),
		  (SELECT COUNT(*) FROM reviews r JOIN properties p ON p.id = r.property_id
		     WHERE p.city_code = c.code AND r.status = ? AND r.deleted_at IS NULL)
		FROM cities c
		WHERE c.live = ?
		ORDER BY c.name, c.code
		""";
	
	private final JdbcTemplate jdbc;
	
	public StatsResponse stats() {
		List<StatsResponse.CityStats> cities = jdbc.query(SQL,
			(rs, i) -> new StatsResponse.CityStats(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
			PropertyStatus.PUBLISHED.name(), TenancyStatus.ACTIVE.name(), TenancyStatus.ENDED.name(),
			ReviewStatus.PUBLISHED.name(), Boolean.TRUE);
		return new StatsResponse(
			cities.stream().mapToLong(StatsResponse.CityStats::publishedProperties).sum(),
			cities.stream().mapToLong(StatsResponse.CityStats::confirmedTenancies).sum(),
			cities.stream().mapToLong(StatsResponse.CityStats::publishedReviews).sum(),
			cities);
	}
	
}
