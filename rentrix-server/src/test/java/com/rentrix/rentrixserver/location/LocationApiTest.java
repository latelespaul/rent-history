package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public reads of the location hierarchy (§12.2, D-107 – D-109, D-112): areas by PIN code and by name or alias
 * prefix, societies, the area and society pages, tower pickers, and the PENDING / HIDDEN rules. Each test runs in a
 * rolled-back transaction on its own live test city, so counts are exact.
 */
@IntegrationTest
@Transactional
class LocationApiTest {
	
	private static final String CITY = "ZQL";
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	PropertyTestSupport support;
	
	@Autowired
	LocationTestSupport locations;
	
	private User landlord;
	
	@BeforeEach
	void city() {
		support.city(CITY, "Location Test City", true);
		landlord = data.user("Location Landlord", Role.LANDLORD);
	}
	
	@Test
	void findsAreasByPincodeIncludingOnePincodeInTwoAreas() throws Exception {
		Area kora = locations.area(CITY, "Koramangala", "Kormangala", LocationStatus.ACTIVE, null, "100095",
			"100034");
		locations.area(CITY, "Ejipura", "100034");
		locations.area(CITY, "HSR Layout", "100102");
		locations.home(landlord, "Kora Home", PropertyStatus.PUBLISHED, kora, null, null, null);
		locations.home(landlord, "Kora Draft", PropertyStatus.DRAFT, kora, null, null, null);
		locations.society(kora, "Kora Greens");
		
		mvc.perform(get("/areas").param("city", CITY).param("pincode", "100034"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalItems").value(2))
			.andExpect(jsonPath("$.items[*].name", contains("Ejipura", "Koramangala")))
			.andExpect(jsonPath("$.items[1].pincodes", contains("100034", "100095")))
			.andExpect(jsonPath("$.items[1].aliases", contains("Kormangala")))
			.andExpect(jsonPath("$.items[1].city.code").value(CITY))
			.andExpect(jsonPath("$.items[1].status").value("ACTIVE"))
			.andExpect(jsonPath("$.items[1].publishedHomes").value(1))
			.andExpect(jsonPath("$.items[1].societyCount").value(1));
		// without a city the PIN code is looked up in every live city
		mvc.perform(get("/areas").param("pincode", "100102"))
			.andExpect(jsonPath("$.items[*].name", contains("HSR Layout")));
		mvc.perform(get("/areas").param("pincode", "1000"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("pincode"));
		mvc.perform(get("/areas").param("city", "NOPE"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("city_not_live"));
	}
	
	@Test
	void findsAreasByWordPrefixOfNameOrAlias() throws Exception {
		locations.area(CITY, "Koramangala", "Kormangala, Kora Block", LocationStatus.ACTIVE, null, "100034");
		locations.area(CITY, "HSR Layout", "100102");
		mvc.perform(get("/areas").param("city", CITY).param("q", " KORA "))
			.andExpect(jsonPath("$.items[*].name", contains("Koramangala")));
		mvc.perform(get("/areas").param("city", CITY).param("q", "korm"))
			.andExpect(jsonPath("$.items[*].name", contains("Koramangala")));
		mvc.perform(get("/areas").param("city", CITY).param("q", "block"))
			.andExpect(jsonPath("$.items[*].name", contains("Koramangala")));
		mvc.perform(get("/areas").param("city", CITY).param("q", "layout"))
			.andExpect(jsonPath("$.items[*].name", contains("HSR Layout")));
		mvc.perform(get("/areas").param("city", CITY).param("q", "ayout"))
			.andExpect(jsonPath("$.items", empty()));
		mvc.perform(get("/areas").param("city", CITY).param("q", "%"))
			.andExpect(jsonPath("$.items", empty()));
		mvc.perform(get("/areas").param("city", CITY).param("sort", "rating"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("sort"));
	}
	
	@Test
	void pendingRowsAreSeenOnlyByTheirProposerAndAdmins() throws Exception {
		User other = data.user("Other Landlord", Role.LANDLORD);
		User admin = data.user("Location Admin", Role.ADMIN);
		Area active = locations.area(CITY, "Active Area", "100001");
		Area proposed = locations.area(CITY, "Proposed Area", null, LocationStatus.PENDING, landlord, "100002");
		Society pendingSociety = locations.society(active, "Proposed Society", null, SocietyKind.APARTMENT_COMPLEX,
			LocationStatus.PENDING, landlord);
		Society society = locations.society(active, "Active Society");
		locations.tower(society, "Tower 1", 0, "Wing A");
		locations.tower(society, "Tower 9", 1, LocationStatus.PENDING, landlord, "Wing Z");
		
		mvc.perform(get("/areas").param("city", CITY))
			.andExpect(jsonPath("$.items[*].name", contains("Active Area")));
		mvc.perform(get("/areas").param("city", CITY).header("Authorization", support.bearer(other)))
			.andExpect(jsonPath("$.items[*].name", contains("Active Area")));
		mvc.perform(get("/areas").param("city", CITY).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.items[*].name", contains("Active Area", "Proposed Area")))
			.andExpect(jsonPath("$.items[1].status").value("PENDING"));
		mvc.perform(get("/areas").param("city", CITY).header("Authorization", support.bearer(admin)))
			.andExpect(jsonPath("$.page.totalItems").value(2));
		
		mvc.perform(get("/areas/{id}", proposed.getPublicId())).andExpect(status().isNotFound());
		mvc.perform(get("/areas/{id}", proposed.getPublicId()).header("Authorization", support.bearer(other)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/areas/{id}", proposed.getPublicId()).header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"));
		
		mvc.perform(get("/societies").param("city", CITY))
			.andExpect(jsonPath("$.items[*].name", contains("Active Society")));
		mvc.perform(get("/areas/{id}", active.getPublicId()))
			.andExpect(jsonPath("$.societies[*].name", contains("Active Society")));
		mvc.perform(get("/areas/{id}", active.getPublicId()).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.societies[*].name", contains("Active Society", "Proposed Society")));
		mvc.perform(get("/societies/{id}", pendingSociety.getPublicId())).andExpect(status().isNotFound());
		mvc.perform(get("/societies/{id}", pendingSociety.getPublicId())
				.header("Authorization", support.bearer(admin)))
			.andExpect(status().isOk());
		
		mvc.perform(get("/societies/{id}/towers", society.getPublicId()))
			.andExpect(jsonPath("$[*].name", contains("Tower 1")));
		mvc.perform(get("/societies/{id}/towers", society.getPublicId()).header("Authorization", support.bearer(other)))
			.andExpect(jsonPath("$[*].name", contains("Tower 1")));
		mvc.perform(get("/societies/{id}/towers", society.getPublicId())
				.header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$[*].name", contains("Tower 1", "Tower 9")))
			.andExpect(jsonPath("$[1].wings[*].name", contains("Wing Z")));
	}
	
	@Test
	void societyPageHasTowersWingsAndPublishedHomeCounts() throws Exception {
		Area area = locations.area(CITY, "Society Area", "100010");
		Society greens = locations.society(area, "Test Greens", "Sobha", SocietyKind.GATED_COMMUNITY,
			LocationStatus.ACTIVE, null);
		locations.society(area, "Plain Complex", null, SocietyKind.APARTMENT_COMPLEX, LocationStatus.ACTIVE, null);
		SocietyTower t1 = locations.tower(greens, "Tower 1", 0, "Wing A", "Wing B");
		SocietyTower t2 = locations.tower(greens, "Tower 2", 1, "Wing A", "Wing B");
		SocietyTower blockC = locations.tower(greens, "Block C", 2);
		SocietyWing t1a = LocationTestSupport.wing(t1, "Wing A");
		locations.home(landlord, "Home 1", PropertyStatus.PUBLISHED, area, greens, t1, t1a);
		locations.home(landlord, "Home 2", PropertyStatus.PUBLISHED, area, greens, t1, t1a);
		locations.home(landlord, "Home 3", PropertyStatus.PUBLISHED, area, greens, t2,
			LocationTestSupport.wing(t2, "Wing B"));
		locations.home(landlord, "Home 4", PropertyStatus.PUBLISHED, area, greens, blockC, null);
		locations.home(landlord, "Draft", PropertyStatus.DRAFT, area, greens, t1, t1a);
		locations.home(landlord, "Standalone", PropertyStatus.PUBLISHED, area, null, null, null);
		
		mvc.perform(get("/societies/{id}", greens.getPublicId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Test Greens"))
			.andExpect(jsonPath("$.developer").value("Sobha"))
			.andExpect(jsonPath("$.kind").value("GATED_COMMUNITY"))
			.andExpect(jsonPath("$.area.name").value("Society Area"))
			.andExpect(jsonPath("$.city.code").value(CITY))
			.andExpect(jsonPath("$.publishedHomes").value(4))
			.andExpect(jsonPath("$.towers[*].name", contains("Tower 1", "Tower 2", "Block C")))
			.andExpect(jsonPath("$.towers[0].publishedHomes").value(2))
			.andExpect(jsonPath("$.towers[0].wings[*].name", contains("Wing A", "Wing B")))
			.andExpect(jsonPath("$.towers[0].wings[*].publishedHomes", contains(2, 0)))
			.andExpect(jsonPath("$.towers[1].wings[*].publishedHomes", contains(0, 1)))
			.andExpect(jsonPath("$.towers[2].publishedHomes").value(1))
			.andExpect(jsonPath("$.towers[2].wings", empty()));
		mvc.perform(get("/societies/{id}/towers", greens.getPublicId()))
			.andExpect(jsonPath("$[*].name", contains("Tower 1", "Tower 2", "Block C")));
		
		mvc.perform(get("/areas/{id}", area.getPublicId()))
			.andExpect(jsonPath("$.publishedHomes").value(5))
			.andExpect(jsonPath("$.standaloneHomes").value(1))
			.andExpect(jsonPath("$.societies[*].name", contains("Plain Complex", "Test Greens")))
			.andExpect(jsonPath("$.societies[1].towerCount").value(3))
			.andExpect(jsonPath("$.societies[1].publishedHomes").value(4));
		
		mvc.perform(get("/societies").param("city", CITY).param("q", "sobha"))
			.andExpect(jsonPath("$.items[*].name", contains("Test Greens")));
		mvc.perform(get("/societies").param("city", CITY).param("kind", "apartment_complex"))
			.andExpect(jsonPath("$.items[*].name", contains("Plain Complex")));
		mvc.perform(get("/societies").param("area", area.getPublicId().toString()))
			.andExpect(jsonPath("$.page.totalItems").value(2));
		mvc.perform(get("/societies").param("kind", "castle"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("kind"));
		mvc.perform(get("/societies").param("area", java.util.UUID.randomUUID().toString()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("area"));
		mvc.perform(get("/societies/{id}", java.util.UUID.randomUUID())).andExpect(status().isNotFound());
	}
	
	@Test
	void hiddenAreaLeavesListsButKeepsItsPropertiesReadable() throws Exception {
		Area hidden = locations.area(CITY, "Hidden Area", null, LocationStatus.HIDDEN, null, "100020");
		Property home = locations.home(landlord, "Hidden Home", PropertyStatus.PUBLISHED, hidden, null, null, null);
		mvc.perform(get("/areas").param("city", CITY))
			.andExpect(jsonPath("$.items[*].name", not(hasItem("Hidden Area"))));
		mvc.perform(get("/areas/{id}", hidden.getPublicId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("HIDDEN"))
			.andExpect(jsonPath("$.publishedHomes").value(1));
		mvc.perform(get("/properties/{id}", home.getPublicId()))
			.andExpect(status().isOk());
		mvc.perform(get("/properties").param("area", hidden.getPublicId().toString()))
			.andExpect(jsonPath("$.items[*].buildingName", contains("Hidden Home")));
	}
	
}
