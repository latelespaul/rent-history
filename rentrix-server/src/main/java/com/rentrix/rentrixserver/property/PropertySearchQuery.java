package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.review.RatingSubjectType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The SQL of {@code GET /properties}, built from the filters with bound parameters only. It is plain SQL that
 * SQLite, MySQL and PostgreSQL all run (D-10), so tests can {@code EXPLAIN} exactly what production runs.
 * <p>Index use: with a city, {@code ix_properties_city_status_locality (city_code, status, locality_key)}
 * narrows to the city's published rows (and the locality prefix); the rating join is by primary key
 * {@code (subject_type, subject_id)}. Without a city the live-city list ({@code cities.live}) drives the same
 * index. With a location filter (D-112) the range is instead {@code ix_properties_area_status (area_id, status)} or
 * {@code ix_properties_society_status (society_id, status, tower_id, wing_id)}; the service has already checked
 * that the area or society is in the requested live city. Free text and facts filter inside that range.
 */
public final class PropertySearchQuery {
	
	/** API sort names (D-59 allow-list); "top_rated" is the Bayesian rank (D-53). */
	public static final String TOP_RATED = "top_rated";
	public static final String MOST_REVIEWED = "most_reviewed";
	public static final String NEWEST = "newest";
	
	/** D-52: below this many counted reviews no average is shown, so it cannot pass a rating filter either. */
	public static final int MIN_REVIEWS_FOR_AVERAGE = 3;
	
	private static final BigDecimal SHOWN_AVERAGE_HALF_STEP = new BigDecimal("0.05");
	
	private static final char LIKE_ESCAPE = '!';
	
	private final String from;
	private final List<Object> params;
	
	private PropertySearchQuery(String from, List<Object> params) {
		this.from = from;
		this.params = List.copyOf(params);
	}
	
	public static PropertySearchQuery of(PropertySearchCriteria criteria) {
		List<Object> params = new ArrayList<>();
		StringBuilder sql = new StringBuilder()
			.append(" FROM properties p")
			.append(" LEFT JOIN rating_aggregates ra ON ra.subject_type = ? AND ra.subject_id = p.id");
		params.add(RatingSubjectType.PROPERTY.name());
		if (criteria.societyId() != null) {
			// a society (and tower) fixes the area and the city: ix_properties_society_status
			sql.append(" WHERE p.society_id = ?");
			params.add(criteria.societyId());
			if (criteria.towerId() != null) {
				sql.append(" AND p.tower_id = ?");
				params.add(criteria.towerId());
			}
		} else if (criteria.areaId() != null) {
			// an area fixes the city: ix_properties_area_status
			sql.append(" WHERE p.area_id = ?");
			params.add(criteria.areaId());
		} else if (criteria.cityCode() != null) {
			sql.append(" WHERE p.city_code = ?");
			params.add(criteria.cityCode());
		} else {
			sql.append(" WHERE p.city_code IN (SELECT c.code FROM cities c WHERE c.live = ?)");
			params.add(Boolean.TRUE);
		}
		sql.append(" AND p.status = ? AND p.deleted_at IS NULL");
		params.add(PropertyStatus.PUBLISHED.name());
		if (Boolean.TRUE.equals(criteria.standalone())) {
			// spelled as COALESCE so it filters inside the area or city range and never competes with that index
			sql.append(" AND COALESCE(p.society_id, 0) = 0");
		} else if (Boolean.FALSE.equals(criteria.standalone())) {
			sql.append(" AND p.society_id IS NOT NULL");
		}
		if (criteria.localityPrefix() != null) {
			sql.append(" AND p.locality_key LIKE ? ESCAPE '").append(LIKE_ESCAPE).append("'");
			params.add(escapeLike(criteria.localityPrefix()) + "%");
		}
		if (criteria.type() != null) {
			sql.append(" AND p.property_type = ?");
			params.add(criteria.type().name());
		}
		if (criteria.bedrooms() != null) {
			sql.append(" AND p.bedrooms = ?");
			params.add(criteria.bedrooms());
		}
		if (criteria.minRating() != null) {
			sql.append(" AND ra.review_count >= ? AND ra.average >= ?");
			params.add(MIN_REVIEWS_FOR_AVERAGE);
			params.add(criteria.minRating().subtract(SHOWN_AVERAGE_HALF_STEP));
		}
		if (criteria.text() != null) {
			String pattern = "%" + escapeLike(criteria.text()) + "%";
			String like = " LIKE ? ESCAPE '" + LIKE_ESCAPE + "'";
			sql.append(" AND (p.locality_key").append(like)
				.append(" OR LOWER(p.building_name)").append(like)
				.append(" OR LOWER(p.street)").append(like).append(")");
			params.add(pattern);
			params.add(pattern);
			params.add(pattern);
		}
		return new PropertySearchQuery(sql.toString(), params);
	}
	
	/** {@code SELECT p.id … ORDER BY … LIMIT … OFFSET …}; parameters are {@link #pageParams}. */
	public String pageSql(String sort, boolean descending) {
		return "SELECT p.id" + from + " ORDER BY " + orderBy(sort, descending) + " LIMIT ? OFFSET ?";
	}
	
	public Object[] pageParams(int limit, long offset) {
		List<Object> all = new ArrayList<>(params);
		all.add(limit);
		all.add(offset);
		return all.toArray();
	}
	
	public String countSql() {
		return "SELECT COUNT(*)" + from;
	}
	
	public Object[] params() {
		return params.toArray();
	}
	
	/**
	 * Unrated homes always sort after rated ones (NULL ordering differs between the three databases, so it is
	 * spelled out); every order ends on the time-ordered id, so pages are stable.
	 */
	private static String orderBy(String sort, boolean descending) {
		String dir = descending ? " DESC" : " ASC";
		return switch (sort) {
			case TOP_RATED -> "CASE WHEN ra.bayes_score IS NULL THEN 1 ELSE 0 END, ra.bayes_score" + dir
				+ ", ra.review_count" + dir + ", ra.last_review_at" + dir + ", p.id DESC";
			case MOST_REVIEWED -> "COALESCE(ra.review_count, 0)" + dir + ", p.id DESC";
			case NEWEST -> "p.id" + dir;
			default -> throw new IllegalArgumentException("Unknown sort " + sort);
		};
	}
	
	static String escapeLike(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (char ch : value.toCharArray()) {
			if (ch == '%' || ch == '_' || ch == LIKE_ESCAPE) {
				escaped.append(LIKE_ESCAPE);
			}
			escaped.append(ch);
		}
		return escaped.toString();
	}
	
}
