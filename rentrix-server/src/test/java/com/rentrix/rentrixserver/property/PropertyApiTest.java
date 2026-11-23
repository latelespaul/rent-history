package com.rentrix.rentrixserver.property;

import com.jayway.jsonpath.JsonPath;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Landlord CRUD, listing states and per-caller fields of {@code /properties} (§5.3, D-21, D-56, D-57, D-92). */
@IntegrationTest
@Transactional
class PropertyApiTest {
	
	private static final String NEW_HOME = """
		{"city": "BLR", "locality": "  Koramangala  ", "buildingName": "Test Residency", "street": "1st Cross",
		 "unitLabel": "T-101", "pincode": "560034", "propertyType": "APARTMENT", "bedrooms": 2, "bathrooms": 2,
		 "areaSqft": 1100, "furnishing": "SEMI", "parking": true, "description": "Near the park."}
		""";
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	PropertyRepository properties;
	
	@Autowired
	PropertyTestSupport support;
	
	private String create(User landlord) throws Exception {
		String body = mvc.perform(post("/properties").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(NEW_HOME))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}
	
	private Property stored(String id) {
		return properties.findByPublicId(UUID.fromString(id)).orElseThrow();
	}
	
	private void publish(String id) {
		Property property = stored(id);
		property.setStatus(PropertyStatus.PUBLISHED);
		properties.saveAndFlush(property);
	}
	
	@Test
	void landlordCreatesDraftWithNormalisedLocality() throws Exception {
		User landlord = data.user("Asha Landlord", Role.LANDLORD);
		mvc.perform(post("/properties").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(NEW_HOME))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/properties/")))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.city.code").value("BLR"))
			.andExpect(jsonPath("$.city.name").value("Bangalore"))
			.andExpect(jsonPath("$.locality").value("Koramangala"))
			.andExpect(jsonPath("$.unitLabel").value("T-101"))
			.andExpect(jsonPath("$.rating.reviewCount").value(0))
			.andExpect(jsonPath("$.landlord.shortName").value("Asha L."))
			.andExpect(jsonPath("$.landlord.id").value(landlord.getPublicId().toString()));
	}
	
	@Test
	void onlyLandlordsCreate() throws Exception {
		User tenant = data.user("Tenant Only", Role.TENANT);
		mvc.perform(post("/properties").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content(NEW_HOME))
			.andExpect(status().isForbidden());
		mvc.perform(post("/properties").contentType(MediaType.APPLICATION_JSON).content(NEW_HOME))
			.andExpect(status().isUnauthorized());
	}
	
	@Test
	void createValidatesFieldsCityAndPincode() throws Exception {
		User landlord = data.user("Valid Landlord", Role.LANDLORD);
		String auth = support.bearer(landlord);
		mvc.perform(post("/properties").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"city\": \"BLR\", \"bedrooms\": -1}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors[?(@.field == 'pincode')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'propertyType')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'bedrooms')]").exists());
		support.city("ZQD", "Dormant Test City", false);
		mvc.perform(post("/properties").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(NEW_HOME.replace("\"BLR\"", "\"ZQD\"")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("city_not_live"));
		mvc.perform(post("/properties").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content(NEW_HOME.replace("560034", "411045")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("pincode"))
			.andExpect(jsonPath("$.errors[0].code").value("city_mismatch"));
	}
	
	@Test
	void draftIsVisibleOnlyToItsOwnerAndAdmins() throws Exception {
		User owner = data.user("Draft Owner", Role.LANDLORD);
		User other = data.user("Other Landlord", Role.LANDLORD);
		User admin = data.user("Some Admin", Role.ADMIN);
		String id = create(owner);
		mvc.perform(get("/properties/" + id)).andExpect(status().isNotFound());
		mvc.perform(get("/properties/" + id).header("Authorization", support.bearer(other)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/properties/" + id).header("Authorization", support.bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unitLabel").value("T-101"));
		mvc.perform(get("/properties/" + id).with(adminHost()).header("Authorization", support.adminBearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unitLabel").value("T-101"));
		// The same account on the main site has no admin powers (D-19).
		mvc.perform(get("/properties/" + id).header("Authorization", support.bearer(admin)))
			.andExpect(status().isNotFound());
		mvc.perform(patch("/properties/" + id).header("Authorization", support.bearer(other))
				.contentType(MediaType.APPLICATION_JSON).content("{\"bedrooms\": 3}"))
			.andExpect(status().isNotFound());
		mvc.perform(get("/properties/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}
	
	@Test
	void publishedPageHidesUnitLabelAndShowsProfileLinkOnlyToSignedInUsers() throws Exception {
		User owner = data.user("Ravi Kumar", Role.LANDLORD);
		User tenant = data.user("Curious Tenant", Role.TENANT);
		String id = create(owner);
		publish(id);
		mvc.perform(get("/properties/" + id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.buildingName").value("Test Residency"))
			.andExpect(jsonPath("$.unitLabel").doesNotExist())
			.andExpect(jsonPath("$.landlord.shortName").value("Ravi K."))
			.andExpect(jsonPath("$.landlord.id").doesNotExist());
		mvc.perform(get("/properties/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.unitLabel").doesNotExist())
			.andExpect(jsonPath("$.landlord.id").value(owner.getPublicId().toString()));
	}
	
	@Test
	void onlyTheOwnerEdits() throws Exception {
		User owner = data.user("Edit Owner", Role.LANDLORD);
		User other = data.user("Not Owner", Role.LANDLORD);
		String id = create(owner);
		publish(id);
		mvc.perform(patch("/properties/" + id).header("Authorization", support.bearer(other))
				.contentType(MediaType.APPLICATION_JSON).content("{\"bedrooms\": 3}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_owner"));
		mvc.perform(post("/properties/" + id + "/submit").header("Authorization", support.bearer(other)))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/properties/" + id).header("Authorization", support.bearer(other)))
			.andExpect(status().isForbidden());
	}
	
	@Test
	void draftEditsLocationAndClearsOptionalText() throws Exception {
		User owner = data.user("Mover Owner", Role.LANDLORD);
		String id = create(owner);
		mvc.perform(patch("/properties/" + id).header("Authorization", support.bearer(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"city\": \"PNQ\", \"pincode\": \"411045\", \"locality\": \"Baner\", \"street\": \"\","
					+ " \"bedrooms\": 3}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.city.code").value("PNQ"))
			.andExpect(jsonPath("$.locality").value("Baner"))
			.andExpect(jsonPath("$.street").doesNotExist())
			.andExpect(jsonPath("$.bedrooms").value(3))
			.andExpect(jsonPath("$.buildingName").value("Test Residency"));
		assertThat(stored(id).getLocalityKey()).isEqualTo("baner");
		mvc.perform(patch("/properties/" + id).header("Authorization", support.bearer(owner))
				.contentType(MediaType.APPLICATION_JSON).content("{\"city\": \"BLR\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("city_mismatch"));
	}
	
	@Test
	void locationIsLockedOnceSubmittedButFactsStayEditable() throws Exception {
		User owner = data.user("Locked Owner", Role.LANDLORD);
		String auth = support.bearer(owner);
		String id = create(owner);
		mvc.perform(post("/properties/" + id + "/submit").header("Authorization", auth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"locality\": \"HSR Layout\", \"buildingName\": \"Test Residency\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("location_locked"))
			.andExpect(jsonPath("$.errors[0].field").value("locality"));
		publish(id);
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pincode\": \"560095\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("location_locked"));
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"locality\": \"Koramangala\", \"description\": \"Freshly painted.\", \"bathrooms\": 3}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("Freshly painted."))
			.andExpect(jsonPath("$.bathrooms").value(3));
	}
	
	@Test
	void otherHomeSizeNeedsWords() throws Exception {
		User landlord = data.user("Other Size Landlord", Role.LANDLORD);
		mvc.perform(post("/properties").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON)
				.content(NEW_HOME.replace("\"bedrooms\": 2,", "\"homeSize\": \"OTHER\", \"homeSizeOther\": \" \",")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("homeSizeOther"));
	}
	
	@Test
	void changingTheTypeKeepsTheHomeSizeInStep() throws Exception {
		User owner = data.user("Type Change Owner", Role.LANDLORD);
		String auth = support.bearer(owner);
		String id = create(owner);
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"propertyType\": \"STUDIO\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.homeSize").value("STUDIO"));
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"propertyType\": \"APARTMENT\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.homeSize").value("BHK2"));
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"propertyType\": \"VILLA\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.homeSize").value("BHK2"));
	}
	
	@Test
	void submitOnlyFromDraftOrRejected() throws Exception {
		User owner = data.user("Submit Owner", Role.LANDLORD);
		String auth = support.bearer(owner);
		String id = create(owner);
		mvc.perform(post("/properties/" + id + "/submit").header("Authorization", auth)).andExpect(status().isOk());
		mvc.perform(post("/properties/" + id + "/submit").header("Authorization", auth))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("invalid_transition"));
		Property property = stored(id);
		property.setStatus(PropertyStatus.REJECTED);
		properties.saveAndFlush(property);
		mvc.perform(post("/properties/" + id + "/submit").header("Authorization", auth))
			.andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
	}
	
	@Test
	void statusPatchOnlyArchivesAPublishedListing() throws Exception {
		User owner = data.user("Archive Owner", Role.LANDLORD);
		String auth = support.bearer(owner);
		String id = create(owner);
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"PUBLISHED\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("status"));
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"ARCHIVED\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("invalid_transition"));
		publish(id);
		mvc.perform(patch("/properties/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"ARCHIVED\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
		// archived stays readable by link (D-21)
		mvc.perform(get("/properties/" + id)).andExpect(status().isOk());
	}
	
	@Test
	void deleteArchivesPublishedAndRemovesNeverPublished() throws Exception {
		User owner = data.user("Delete Owner", Role.LANDLORD);
		String auth = support.bearer(owner);
		String published = create(owner);
		publish(published);
		String draft = create(owner);
		mvc.perform(delete("/properties/" + published).header("Authorization", auth)).andExpect(status().isNoContent());
		mvc.perform(delete("/properties/" + published).header("Authorization", auth)).andExpect(status().isNoContent());
		assertThat(stored(published).getStatus()).isEqualTo(PropertyStatus.ARCHIVED);
		mvc.perform(delete("/properties/" + draft).header("Authorization", auth)).andExpect(status().isNoContent());
		assertThat(stored(draft).getDeletedAt()).isNotNull();
		mvc.perform(get("/properties/" + draft).header("Authorization", auth)).andExpect(status().isNotFound());
	}
	
	@Test
	void mineListsOwnListingsInEveryState() throws Exception {
		User owner = data.user("Portfolio Owner", Role.LANDLORD);
		User other = data.user("Someone Else", Role.LANDLORD);
		String auth = support.bearer(owner);
		String a = create(owner);
		String b = create(owner);
		publish(b);
		create(other);
		mvc.perform(get("/me/properties").header("Authorization", auth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalItems").value(2))
			.andExpect(jsonPath("$.items[?(@.id == '" + a + "')].status").value("DRAFT"))
			.andExpect(jsonPath("$.items[?(@.id == '" + b + "')].status").value("PUBLISHED"))
			.andExpect(jsonPath("$.items[0].unitLabel").value("T-101"));
		mvc.perform(get("/me/properties")).andExpect(status().isUnauthorized());
		mvc.perform(get("/me/properties").param("sort", "rent").header("Authorization", auth))
			.andExpect(status().isUnprocessableContent());
	}
	
}
