package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationTestSupport;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code location} on {@code POST} / {@code PATCH /properties} (D-109, D-110): picked rows, proposals, lock. */
@IntegrationTest
@Transactional
class PropertyPlacementTest {

	private static final String CITY = "ZQP";

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	LocationTestSupport locations;

	private String auth;
	private Area area;
	private Society greens;
	private SocietyTower tower;

	@BeforeEach
	void places() {
		support.city(CITY, "Placement Test City", true);
		User landlord = data.user("Placement Landlord", Role.LANDLORD);
		auth = support.bearer(landlord);
		area = locations.area(CITY, "Placement Area", "300101", "300102");
		greens = locations.society(area, "Placement Greens");
		tower = locations.tower(greens, "Tower 1", 0, "Wing A");
	}

	private String body(String location) {
		return """
			{"city": "%s", "pincode": "300101", "buildingName": "Placed Home", "propertyType": "APARTMENT",
			 "location": %s}""".formatted(CITY, location);
	}

	@Test
	void createPlacesTheHomeAndCopiesTheLocalityFromTheArea() throws Exception {
		String location = """
			{"areaId": "%s", "societyId": "%s", "towerId": "%s", "wingId": "%s"}""".formatted(area.getPublicId(),
			greens.getPublicId(), tower.getPublicId(), LocationTestSupport.wing(tower, "Wing A").getPublicId());
		mvc.perform(post("/properties").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(body(location)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.locality").value("Placement Area"))
			.andExpect(jsonPath("$.location.area.name").value("Placement Area"))
			.andExpect(jsonPath("$.location.society.name").value("Placement Greens"))
			.andExpect(jsonPath("$.location.tower.name").value("Tower 1"))
			.andExpect(jsonPath("$.location.wing.name").value("Wing A"))
			.andExpect(jsonPath("$.location.wing.status").value("ACTIVE"));
	}

	@Test
	void createWithAProposalSavesItPending() throws Exception {
		String location = """
			{"areaId": "%s", "newSociety": {"name": "Proposed Park", "kind": "APARTMENT_COMPLEX"}}"""
			.formatted(area.getPublicId());
		mvc.perform(post("/properties").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(body(location)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.location.society.name").value("Proposed Park"))
			.andExpect(jsonPath("$.location.society.status").value("PENDING"))
			.andExpect(jsonPath("$.location.tower").doesNotExist());
	}

	@Test
	void createChecksThePlacementRules() throws Exception {
		mvc.perform(post("/properties").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(body("{\"towerId\": \"%s\"}".formatted(tower.getPublicId()))))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[?(@.field == 'location.areaId')]").exists());
		mvc.perform(post("/properties").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(body("{\"areaId\": \"%s\"}".formatted(area.getPublicId())).replace("300101", "300109")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("pincode_not_in_area"));
		mvc.perform(post("/properties").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"city": "%s", "pincode": "300101", "propertyType": "APARTMENT"}""".formatted(CITY)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[?(@.field == 'locality')]").exists());
	}

	@Test
	void patchMovesADraftAndLocksASubmittedListing() throws Exception {
		String created = mvc.perform(post("/properties").header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("{\"areaId\": \"%s\"}".formatted(area.getPublicId()))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.location.society").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");
		String move = """
			{"location": {"areaId": "%s", "societyId": "%s", "towerId": "%s"}}""".formatted(area.getPublicId(),
			greens.getPublicId(), tower.getPublicId());
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(move))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.location.tower.name").value("Tower 1"))
			.andExpect(jsonPath("$.location.wing").doesNotExist());
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pincode\": \"300102\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pincode").value("300102"))
			.andExpect(jsonPath("$.location.tower.name").value("Tower 1"));
		mvc.perform(post("/properties/" + id + "/submit").header("Authorization", auth))
			.andExpect(status().isOk());
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(move))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("location_locked"));
	}

}
