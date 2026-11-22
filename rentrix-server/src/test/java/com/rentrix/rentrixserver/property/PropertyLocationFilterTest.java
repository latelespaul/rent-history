package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.LocationTestSupport;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyKind;
import com.rentrix.rentrixserver.location.SocietyTower;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The location filters of {@code GET /properties} (D-112, PLAN B4b): area, society, tower, standalone. */
@IntegrationTest
@Transactional
class PropertyLocationFilterTest {
	
	private static final String CITY = "ZQF";
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	PropertyTestSupport support;
	
	@Autowired
	LocationTestSupport locations;
	
	@Autowired
	JdbcTemplate jdbc;
	
	private User landlord;
	private Area area;
	private Society greens;
	private SocietyTower t1;
	private SocietyTower t2;
	
	@BeforeEach
	void homes() {
		support.city(CITY, "Filter Test City", true);
		landlord = data.user("Filter Landlord", Role.LANDLORD);
		area = locations.area(CITY, "Filter Area", "300001");
		Area otherArea = locations.area(CITY, "Other Filter Area", "300002");
		greens = locations.society(area, "Filter Greens");
		t1 = locations.tower(greens, "Tower 1", 0, "Wing A");
		t2 = locations.tower(greens, "Tower 2", 1);
		locations.home(landlord, "In T1", PropertyStatus.PUBLISHED, area, greens, t1, LocationTestSupport.wing(t1, "Wing A"));
		locations.home(landlord, "In T2", PropertyStatus.PUBLISHED, area, greens, t2, null);
		locations.home(landlord, "Standalone", PropertyStatus.PUBLISHED, area, null, null, null);
		locations.home(landlord, "Draft In T1", PropertyStatus.DRAFT, area, greens, t1, null);
		locations.home(landlord, "Elsewhere", PropertyStatus.PUBLISHED, otherArea, null, null, null);
	}
	
	@Test
	void filtersByAreaSocietyTowerAndStandalone() throws Exception {
		mvc.perform(get("/properties").param("area", area.getPublicId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].buildingName", containsInAnyOrder("In T1", "In T2", "Standalone")));
		mvc.perform(get("/properties").param("city", CITY).param("area", area.getPublicId().toString())
				.param("standalone", "true"))
			.andExpect(jsonPath("$.items[*].buildingName", containsInAnyOrder("Standalone")));
		mvc.perform(get("/properties").param("area", area.getPublicId().toString()).param("standalone", "false"))
			.andExpect(jsonPath("$.items[*].buildingName", containsInAnyOrder("In T1", "In T2")));
		mvc.perform(get("/properties").param("city", CITY).param("standalone", "true"))
			.andExpect(jsonPath("$.items[*].buildingName", containsInAnyOrder("Standalone", "Elsewhere")));
		mvc.perform(get("/properties").param("society", greens.getPublicId().toString()))
			.andExpect(jsonPath("$.items[*].buildingName", containsInAnyOrder("In T1", "In T2")));
		mvc.perform(get("/properties").param("tower", t1.getPublicId().toString()))
			.andExpect(jsonPath("$.items[*].buildingName", containsInAnyOrder("In T1")));
		mvc.perform(get("/properties").param("area", area.getPublicId().toString())
				.param("society", greens.getPublicId().toString()).param("tower", t2.getPublicId().toString()))
			.andExpect(jsonPath("$.items[*].buildingName", containsInAnyOrder("In T2")));
	}
	
	@Test
	void rejectsUnknownMismatchedAndContradictoryFilters() throws Exception {
		Area otherArea = locations.area(CITY, "Third Area", "300003");
		Society otherSociety = locations.society(otherArea, "Other Greens");
		SocietyTower otherTower = locations.tower(otherSociety, "Tower 9", 0);
		Society proposed = locations.society(area, "Proposed", null, SocietyKind.APARTMENT_COMPLEX,
			LocationStatus.PENDING, data.user("Proposer", Role.LANDLORD));
		mvc.perform(get("/properties").param("area", UUID.randomUUID().toString()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("area"))
			.andExpect(jsonPath("$.errors[0].code").value("not_found"));
		mvc.perform(get("/properties").param("society", proposed.getPublicId().toString()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("society"));
		mvc.perform(get("/properties").param("area", "not-a-uuid"))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/properties").param("society", greens.getPublicId().toString())
				.param("tower", otherTower.getPublicId().toString()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("location_mismatch"));
		mvc.perform(get("/properties").param("area", otherArea.getPublicId().toString())
				.param("society", greens.getPublicId().toString()))
			.andExpect(jsonPath("$.code").value("location_mismatch"));
		mvc.perform(get("/properties").param("city", "BLR").param("area", area.getPublicId().toString()))
			.andExpect(jsonPath("$.code").value("location_mismatch"));
		mvc.perform(get("/properties").param("society", greens.getPublicId().toString()).param("standalone", "true"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("standalone"));
	}
	
	@Test
	void locationFiltersUseTheirIndexes() {
		Map<PropertySearchCriteria, String> expected = Map.of(
			criteria(area.getId(), null, null, null), "ix_properties_area_status",
			criteria(area.getId(), null, null, true), "ix_properties_area_status",
			criteria(null, greens.getId(), null, null), "ix_properties_society_status",
			criteria(null, greens.getId(), t1.getId(), false), "ix_properties_society_status",
			new PropertySearchCriteria(CITY, null, null, null, null, null, null, null, null, true),
			"ix_properties_city_status_locality");
		expected.forEach((criteria, index) -> {
			PropertySearchQuery query = PropertySearchQuery.of(criteria);
			for (String sort : List.of(PropertySearchQuery.TOP_RATED, PropertySearchQuery.NEWEST)) {
				String plan = plan(query.pageSql(sort, true), query.pageParams(20, 0));
				assertThat(plan).as(criteria.toString()).contains("USING INDEX " + index)
					.doesNotContain("SCAN p").doesNotContain("SCAN ra");
			}
			assertThat(plan(query.countSql(), query.params())).contains(index);
		});
	}
	
	private static PropertySearchCriteria criteria(Long areaId, Long societyId, Long towerId, Boolean standalone) {
		return new PropertySearchCriteria(null, null, null, null, null, null, areaId, societyId, towerId, standalone);
	}
	
	private String plan(String sql, Object[] params) {
		List<Map<String, Object>> rows = jdbc.queryForList("EXPLAIN QUERY PLAN " + sql, params);
		return rows.stream().map(row -> String.valueOf(row.get("detail"))).reduce("", (a, b) -> a + "\n" + b);
	}
	
}
