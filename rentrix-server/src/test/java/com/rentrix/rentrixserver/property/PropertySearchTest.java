package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.LocationTestSupport;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyKind;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /properties} filters, sorts, pagination and index use (§12.2, D-52, D-53, D-59). Each test runs in
 * a rolled-back transaction on its own live test city, so counts are exact whatever else the suite stored.
 */
@IntegrationTest
@Transactional
class PropertySearchTest {
	
	private static final String CITY = "ZQS";
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	PropertyRepository properties;
	
	@Autowired
	PropertyTestSupport support;
	
	@Autowired
	JdbcTemplate jdbc;
	
	@Autowired
	LocationTestSupport locations;
	
	private User landlord;
	
	@BeforeEach
	void city() {
		support.city(CITY, "Search Test City", true);
		landlord = data.user("Search Landlord", Role.LANDLORD);
	}
	
	private Property home(String building, String locality, PropertyType type, Integer bedrooms,
		PropertyStatus status) {
		Property property = new Property();
		property.setLandlord(landlord);
		property.setCity(data.city(CITY));
		property.setLocality(locality);
		property.setBuildingName(building);
		property.setStreet("Main Road");
		property.setUnitLabel("U-1");
		property.setPincode("100001");
		property.setPropertyType(type);
		property.setBedrooms(bedrooms);
		property.setStatus(status);
		return properties.saveAndFlush(property);
	}
	
	@Test
	void listsOnlyPublishedHomesOfTheCityWithPublicFields() throws Exception {
		home("Alpha", "Koramangala", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		home("Draft", "Koramangala", PropertyType.APARTMENT, 2, PropertyStatus.DRAFT);
		home("Pending", "Koramangala", PropertyType.APARTMENT, 2, PropertyStatus.PENDING_REVIEW);
		home("Archived", "Koramangala", PropertyType.APARTMENT, 2, PropertyStatus.ARCHIVED);
		Property deleted = home("Deleted", "Koramangala", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		deleted.setDeletedAt(java.time.Instant.now());
		properties.saveAndFlush(deleted);
		mvc.perform(get("/properties").param("city", CITY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].buildingName").value("Alpha"))
			.andExpect(jsonPath("$.items[0].city.code").value(CITY))
			.andExpect(jsonPath("$.items[0].landlordLabel").value("Owner"))
			.andExpect(jsonPath("$.items[0].rating.reviewCount").value(0))
			.andExpect(jsonPath("$.items[0].unitLabel").doesNotExist());
	}
	
	@Test
	void filtersByLocalityPrefixTypeBedroomsAndText() throws Exception {
		home("Alpha Residency", "Koramangala", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		home("Beta Villa", "Koramangala 5th Block", PropertyType.VILLA, 3, PropertyStatus.PUBLISHED);
		home("Gamma Court", "HSR Layout", PropertyType.APARTMENT, 1, PropertyStatus.PUBLISHED);
		mvc.perform(get("/properties").param("city", CITY).param("locality", "  KORA "))
			.andExpect(jsonPath("$.page.totalItems").value(2));
		mvc.perform(get("/properties").param("city", CITY).param("type", "apartment"))
			.andExpect(jsonPath("$.page.totalItems").value(2));
		mvc.perform(get("/properties").param("city", CITY).param("bedrooms", "3"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Beta Villa")));
		mvc.perform(get("/properties").param("city", CITY).param("q", "court"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Gamma Court")));
		mvc.perform(get("/properties").param("city", CITY).param("q", "hsr"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Gamma Court")));
		mvc.perform(get("/properties").param("city", CITY).param("q", "main road"))
			.andExpect(jsonPath("$.page.totalItems").value(3));
		// LIKE wildcards in user input are literal
		mvc.perform(get("/properties").param("city", CITY).param("q", "%"))
			.andExpect(jsonPath("$.page.totalItems").value(0));
		mvc.perform(get("/properties").param("city", CITY).param("locality", "kora").param("type", "VILLA"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Beta Villa")));
	}
	
	@Test
	void minRatingNeedsThreeReviewsAndTopRatedSortsByBayes() throws Exception {
		Property strong = home("Strong", "Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		Property many = home("Many", "Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		Property fresh = home("Fresh", "Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		home("Unrated", "Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		support.rating(strong, 3, 5.0);  // bayes (17.5+15)/8 = 4.063
		support.rating(many, 10, 4.2);   // bayes (17.5+42)/15 = 3.967
		support.rating(fresh, 2, 5.0);   // bayes (17.5+10)/7 = 3.929, no average shown
		mvc.perform(get("/properties").param("city", CITY))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Strong", "Many", "Fresh", "Unrated")))
			.andExpect(jsonPath("$.items[0].rating.average").value(5.0))
			.andExpect(jsonPath("$.items[2].rating.reviewCount").value(2))
			.andExpect(jsonPath("$.items[2].rating.average").doesNotExist());
		mvc.perform(get("/properties").param("city", CITY).param("minRating", "4.1"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Strong", "Many")));
		mvc.perform(get("/properties").param("city", CITY).param("minRating", "4.5"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Strong")));
		mvc.perform(get("/properties").param("city", CITY).param("sort", "most_reviewed,desc"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Many", "Strong", "Fresh", "Unrated")));
		mvc.perform(get("/properties").param("city", CITY).param("sort", "top_rated,asc"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Fresh", "Many", "Strong", "Unrated")));
		mvc.perform(get("/properties").param("city", CITY).param("sort", "newest,desc"))
			.andExpect(jsonPath("$.items[0].buildingName").value("Unrated"));
	}
	
	@Test
	void bareSortNamesSortDescending() throws Exception {
		Property strong = home("Strong", "Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		Property many = home("Many", "Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		home("Unrated", "Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		support.rating(strong, 3, 5.0);
		support.rating(many, 10, 4.2);
		mvc.perform(get("/properties").param("city", CITY).param("sort", "top_rated"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Strong", "Many", "Unrated")));
		mvc.perform(get("/properties").param("city", CITY).param("sort", "newest"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Unrated", "Many", "Strong")));
	}
	
	@Test
	void minRatingMatchesTheShownOneDecimalAverage() throws Exception {
		Property shownFour = home("Shown Four", "Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		Property shownThreeNine = home("Shown Three Nine", "Area", PropertyType.APARTMENT, 2,
			PropertyStatus.PUBLISHED);
		support.rating(shownFour, 50, 3.96);
		support.rating(shownThreeNine, 50, 3.94);
		mvc.perform(get("/properties").param("city", CITY).param("minRating", "4"))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Shown Four")))
			.andExpect(jsonPath("$.items[0].rating.average").value(4.0));
	}
	
	@Test
	void paginatesWithTheD59Envelope() throws Exception {
		for (int i = 0; i < 5; i++) {
			home("Home " + i, "Area", PropertyType.APARTMENT, 1, PropertyStatus.PUBLISHED);
		}
		mvc.perform(get("/properties").param("city", CITY).param("size", "2").param("page", "2")
				.param("sort", "newest,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].buildingName").value("Home 4"))
			.andExpect(jsonPath("$.page.number").value(2))
			.andExpect(jsonPath("$.page.size").value(2))
			.andExpect(jsonPath("$.page.totalItems").value(5))
			.andExpect(jsonPath("$.page.totalPages").value(3));
		mvc.perform(get("/properties").param("city", CITY).param("page", "9"))
			.andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.page.totalItems").value(5));
	}
	
	/**
	 * D-162 (captain 2026-11-24 00:31): anyone, signed in or not, finds a tenant's new home by its society, even a
	 * typed one still pending review, by its PIN code and by its city's name; the card is tagged pending.
	 */
	@Test
	void textFindsATypedPendingSocietyThePinCodeAndTheCityAnonymously() throws Exception {
		Area area = locations.area(CITY, "Search Layout", "560102");
		Society typed = locations.society(area, "Sobha Typed Greens", null, SocietyKind.APARTMENT_COMPLEX,
			LocationStatus.PENDING, data.user("Typing Tenant", Role.TENANT));
		Property home = home(null, "Search Layout", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		home.setArea(area);
		home.setSociety(typed);
		home.setPincode("560102");
		home.setStreet(null);
		properties.saveAndFlush(home);
		home("Elsewhere", "Other Area", PropertyType.APARTMENT, 2, PropertyStatus.PUBLISHED);
		for (String q : List.of("sobha", "TYPED greens", "560102", "5601")) {
			mvc.perform(get("/properties").param("city", CITY).param("q", q))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalItems").value(1))
				.andExpect(jsonPath("$.items[0].id").value(home.getPublicId().toString()))
				.andExpect(jsonPath("$.items[0].locationPending").value(true))
				.andExpect(jsonPath("$.items[0].verification").value("UNVERIFIED"));
		}
		mvc.perform(get("/properties").param("q", "search test city").param("size", "50"))
			.andExpect(jsonPath("$.page.totalItems").value(2));
		mvc.perform(get("/properties").param("city", CITY).param("q", "search layout"))
			.andExpect(jsonPath("$.items[*].id", contains(home.getPublicId().toString())));
	}
	
	@Test
	void withoutCitySearchesEveryLiveCityOnly() throws Exception {
		support.city("ZQX", "Dormant Search City", false);
		home("Live Home", "Area", PropertyType.APARTMENT, 1, PropertyStatus.PUBLISHED);
		Property dormant = new Property();
		dormant.setLandlord(landlord);
		dormant.setCity(data.city("ZQX"));
		dormant.setLocality("Area");
		dormant.setBuildingName("Dormant Home");
		dormant.setPincode("100001");
		dormant.setPropertyType(PropertyType.APARTMENT);
		dormant.setStatus(PropertyStatus.PUBLISHED);
		properties.saveAndFlush(dormant);
		mvc.perform(get("/properties").param("q", "home").param("size", "50"))
			.andExpect(jsonPath("$.items[?(@.buildingName == 'Live Home')]").exists())
			.andExpect(jsonPath("$.items[?(@.buildingName == 'Dormant Home')]").doesNotExist());
		mvc.perform(get("/properties").param("city", "ZQX"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("city_not_live"));
		mvc.perform(get("/properties").param("city", "NOPE"))
			.andExpect(jsonPath("$.code").value("city_not_live"));
	}
	
	@Test
	void rejectsBadFiltersAndUnknownSorts() throws Exception {
		mvc.perform(get("/properties").param("type", "castle").param("bedrooms", "-1").param("minRating", "6"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors[*].field", contains("type", "bedrooms", "minRating")));
		mvc.perform(get("/properties").param("sort", "rent,desc"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("sort"));
		mvc.perform(get("/properties").param("size", "51"))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(get("/properties").param("q", "x".repeat(101)))
			.andExpect(jsonPath("$.errors[0].field").value("q"));
	}
	
	@Test
	void cityQueryUsesTheCitySearchIndex() {
		for (PropertySearchCriteria criteria : List.of(
			new PropertySearchCriteria(CITY, null, null, null, null, null),
			new PropertySearchCriteria(null, null, null, 1, null, null),
			new PropertySearchCriteria(CITY, "kora", PropertyType.APARTMENT, 2, BigDecimal.valueOf(4), "court"))) {
			PropertySearchQuery query = PropertySearchQuery.of(criteria);
			for (String sort : List.of(PropertySearchQuery.TOP_RATED, PropertySearchQuery.NEWEST)) {
				String plan = plan(query.pageSql(sort, true), query.pageParams(20, 0));
				assertThat(plan).contains("USING INDEX ix_properties_city_status_locality");
				// the rating join (when SQLite keeps it) goes by its (subject_type, subject_id) key: no table scan
				assertThat(plan).doesNotContain("SCAN p").doesNotContain("SCAN ra");
			}
			assertThat(plan(query.countSql(), query.params())).contains("ix_properties_city_status_locality");
		}
	}
	
	private String plan(String sql, Object[] params) {
		List<Map<String, Object>> rows = jdbc.queryForList("EXPLAIN QUERY PLAN " + sql, params);
		return rows.stream().map(row -> String.valueOf(row.get("detail"))).reduce("", (a, b) -> a + "\n" + b);
	}
	
}
