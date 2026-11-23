package com.rentrix.rentrixserver.edit;

import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationTestSupport;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.verification.VerificationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.rentrix.rentrixserver.support.AdminConsole.adminBearer;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owners edit their own entries (D-156): only the creator changes or deletes a tenancy, a home or a rent record; an
 * unverified item changes freely; a verified one loses its tick and is back in the admin queue; every change is kept,
 * field by field, in an edit history only admins read.
 */
@IntegrationTest
@Transactional
class OwnEditApiTest {

	private static final String CITY = "ZQE";
	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	LocationTestSupport locations;

	@Autowired
	ObjectMapper json;

	@Autowired
	AccessTokenService tokens;

	@Autowired
	TenancyRepository tenancyRows;

	@Autowired
	PropertyRepository propertyRows;

	@Autowired
	JdbcTemplate jdbc;

	private Area area;
	private Society society;

	@BeforeEach
	void places() {
		support.city(CITY, "Edit Test City", true);
		jdbc.update("update cities set pincode_prefixes = '30' where code = ?", CITY);
		area = locations.area(CITY, "Edit Area", "300301", "300302");
		society = locations.society(area, "Palm Grove");
	}

	private User tenant(String name) {
		User user = data.verified(name, Role.TENANT);
		data.verifyPhone(user);
		return user;
	}

	/** A home the tenant adds with their tenancy since {@code start}; returns the add-home answer. */
	private JsonNode addHome(User tenant, String unit, LocalDate start) throws Exception {
		String body = """
			{"home": {"city": "%s", "pincode": "300301", "unitLabel": "%s", "propertyType": "APARTMENT",
			  "homeSize": "BHK2", "location": {"areaId": "%s", "societyId": "%s"}},
			 "relation": "RENT", "startDate": "%s", "monthlyRentInr": 30000, "depositInr": 90000}"""
			.formatted(CITY, unit, area.getPublicId(), society.getPublicId(), start);
		return json.readTree(mvc.perform(post("/me/homes").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private ResultActions patchJson(User user, String path, String body) throws Exception {
		return mvc.perform(patch(path).header("Authorization", support.bearer(user))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String tenancyBody(LocalDate start, LocalDate end, Long rent, Long deposit) {
		return """
			{"startDate": "%s", "endDate": %s, "initialRentInr": %s, "depositInr": %s}"""
			.formatted(start, end == null ? "null" : "\"" + end + "\"", rent, deposit);
	}

	private JsonNode history(User admin, String type, String id) throws Exception {
		return json.readTree(mvc.perform(get("/admin/edit-history").with(adminHost())
				.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)).param("type", type).param("id", id))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private User admin() {
		return data.user("Edit Admin " + UUID.randomUUID(), Role.ADMIN);
	}

	@Test
	void anUnverifiedTenancyIsEditedFreelyAndTheHistoryKeepsEveryField() throws Exception {
		User tenant = tenant("Edit Tenant");
		JsonNode added = addHome(tenant, "A-101", TODAY.minusMonths(6));
		String id = added.at("/tenancy/id").asString();
		assertThat(added.at("/tenancy/actions/edit").asBoolean()).isTrue();
		assertThat(added.at("/property/editable").asBoolean()).isTrue();

		LocalDate start = TODAY.minusMonths(7).withDayOfMonth(5);
		patchJson(tenant, "/tenancies/" + id, tenancyBody(start, null, 32000L, 64000L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.startDate").value(start.toString()))
			.andExpect(jsonPath("$.initialRentInr").value(32000))
			.andExpect(jsonPath("$.depositInr").value(64000))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
		// The starting rent follows the edit in the home's rent history.
		String home = added.at("/property/id").asString();
		mvc.perform(get("/properties/" + home + "/rent-history").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.rows.length()").value(1))
			.andExpect(jsonPath("$.rows[0].monthlyRentInr").value(32000))
			.andExpect(jsonPath("$.rows[0].fromMonth").value(start.toString().substring(0, 7)));

		JsonNode rows = history(admin(), "TENANCY", id);
		assertThat(rows.size()).isEqualTo(3);
		assertThat(rows.findValuesAsString("field")).containsExactlyInAnyOrder("startDate", "initialRentInr",
			"depositInr");
		assertThat(rows.get(0).get("wasVerified").asBoolean()).isFalse();
		assertThat(rows.get(0).get("editorName").asString()).isEqualTo("Edit Tenant");

		// Moving out: the end date makes it ENDED.
		patchJson(tenant, "/tenancies/" + id, tenancyBody(start, TODAY.minusDays(3), 32000L, 64000L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENDED"));
		// Same checks as on create, by field.
		patchJson(tenant, "/tenancies/" + id, tenancyBody(TODAY.plusDays(2), null, 32000L, null))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("startDate"));
	}

	@Test
	void editingAVerifiedTenancyTakesTheTickAwayAndPutsItBackInTheQueue() throws Exception {
		User tenant = tenant("Ticked Tenant");
		JsonNode added = addHome(tenant, "B-202", TODAY.minusMonths(4));
		String id = added.at("/tenancy/id").asString();
		User admin = admin();
		mvc.perform(post("/admin/tenancies/" + id + "/approve").with(adminHost())
				.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)))
			.andExpect(status().isOk());
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

		patchJson(tenant, "/tenancies/" + id, tenancyBody(TODAY.minusMonths(4), null, 31000L, 90000L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
		Tenancy row = tenancyRows.findByPublicId(UUID.fromString(id)).orElseThrow();
		assertThat(row.getVerificationDecidedBy()).isNull();

		mvc.perform(get("/admin/queue").with(adminHost()).header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin))
				.param("type", "TENANCY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '%s')].flags[0]".formatted(id)).value("EDITED"));
		JsonNode rows = history(admin, "TENANCY", id);
		assertThat(rows.get(0).get("field").asString()).isEqualTo("initialRentInr");
		assertThat(rows.get(0).get("before").asString()).isEqualTo("30000");
		assertThat(rows.get(0).get("after").asString()).isEqualTo("31000");
		assertThat(rows.get(0).get("wasVerified").asBoolean()).isTrue();
	}

	@Test
	void onlyTheCreatorEditsOrDeletes() throws Exception {
		User tenant = tenant("Creator Tenant");
		JsonNode added = addHome(tenant, "C-303", TODAY.minusMonths(3));
		String tenancy = added.at("/tenancy/id").asString();
		String home = added.at("/property/id").asString();
		User stranger = tenant("Stranger");

		// Another user cannot even see the tenancy (D-60), and cannot change the home.
		patchJson(stranger, "/tenancies/" + tenancy, tenancyBody(TODAY.minusMonths(3), null, 1L, null))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/tenancies/" + tenancy).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		patchJson(stranger, "/me/homes/" + home, "{\"pincode\": \"300301\", \"unitLabel\": \"X\"}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_creator"));
		mvc.perform(delete("/me/homes/" + home).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/properties/" + home).header("Authorization", support.bearer(stranger)))
			.andExpect(jsonPath("$.editable").value(false));
	}

	@Test
	void theCreatorEditsTheirHomeAndAVerifiedHomeGoesBackToTheQueue() throws Exception {
		User tenant = tenant("Home Tenant");
		JsonNode added = addHome(tenant, "D-404", TODAY.minusMonths(3));
		String home = added.at("/property/id").asString();
		Property row = propertyRows.findByPublicIdAndDeletedAtIsNull(UUID.fromString(home)).orElseThrow();
		row.setVerificationStatus(VerificationState.VERIFIED);
		propertyRows.saveAndFlush(row);

		patchJson(tenant, "/me/homes/" + home, """
			{"pincode": "300302", "unitLabel": "D-405", "homeSize": "OTHER", "homeSizeOther": "2.5 BHK",
			 "location": {"areaId": "%s", "newSociety": {"name": "Palm Grove Phase 2", "kind": "GATED_COMMUNITY"}}}"""
				.formatted(area.getPublicId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unitLabel").value("D-405"))
			.andExpect(jsonPath("$.pincode").value("300302"))
			.andExpect(jsonPath("$.homeSize").value("OTHER"))
			.andExpect(jsonPath("$.homeSizeOther").value("2.5 BHK"))
			.andExpect(jsonPath("$.location.society.name").value("Palm Grove Phase 2"))
			.andExpect(jsonPath("$.verification").value("UNVERIFIED"));
		JsonNode rows = history(admin(), "PROPERTY", home);
		assertThat(rows.findValuesAsString("field")).containsExactlyInAnyOrder("society", "unitLabel", "pincode",
			"homeSize");
		assertThat(rows.get(0).get("wasVerified").asBoolean()).isTrue();

		// The add-home checks, by field: a PIN code outside the city, size "Other" without words.
		patchJson(tenant, "/me/homes/" + home, "{\"pincode\": \"999999\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("pincode"));
		patchJson(tenant, "/me/homes/" + home, "{\"pincode\": \"300301\", \"homeSize\": \"OTHER\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("homeSizeOther"));
	}

	@Test
	void aHomeWithOtherPeoplesTenanciesKeepsItsAddress() throws Exception {
		User first = tenant("First Edit Tenant");
		JsonNode added = addHome(first, "E-505", TODAY.minusYears(2));
		String home = added.at("/property/id").asString();
		// The first tenancy has ended; a second tenant records theirs on the same home.
		patchJson(first, "/tenancies/" + added.at("/tenancy/id").asString(),
			tenancyBody(TODAY.minusYears(2), TODAY.minusMonths(8), 30000L, 90000L)).andExpect(status().isOk());
		User second = tenant("Second Edit Tenant");
		mvc.perform(post("/me/homes").header("Authorization", support.bearer(second))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"propertyId": "%s", "relation": "RENT", "startDate": "%s", "monthlyRentInr": 33000}"""
					.formatted(home, TODAY.minusMonths(5))))
			.andExpect(status().isCreated());

		patchJson(first, "/me/homes/" + home, "{\"pincode\": \"300301\", \"unitLabel\": \"E-506\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("location_locked"))
			.andExpect(jsonPath("$.errors[0].field").value("unitLabel"));
		// The size may still change.
		patchJson(first, "/me/homes/" + home, "{\"pincode\": \"300301\", \"unitLabel\": \"E-505\", \"homeSize\": \"BHK3\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.homeSize").value("BHK3"));
		mvc.perform(delete("/me/homes/" + home).header("Authorization", support.bearer(first)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("home_in_use"));
	}

	@Test
	void deletingATenancyCancelsItAndWithdrawsTheCreatorsReviews() throws Exception {
		User tenant = tenant("Delete Tenant");
		JsonNode added = addHome(tenant, "F-606", TODAY.minusMonths(9));
		String id = added.at("/tenancy/id").asString();
		String home = added.at("/property/id").asString();
		mvc.perform(post("/tenancies/" + id + "/reviews").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"direction": "TENANT_TO_PROPERTY", "ratingOverall": 4,
					 "body": "Bright flat, quick repairs and a fair deposit return; the lifts work well too."}"""))
			.andExpect(status().isCreated());

		mvc.perform(delete("/tenancies/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isNoContent());
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.actions.edit").value(false));
		mvc.perform(get("/properties/" + home + "/reviews").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.items.length()").value(0));
		mvc.perform(get("/properties/" + home + "/rent-history").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.rows.length()").value(0));
		assertThat(history(admin(), "TENANCY", id).get(0).get("change").asString()).isEqualTo("DELETED");

		// With nothing left on it, the home itself can go.
		mvc.perform(delete("/me/homes/" + home).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isNoContent());
		mvc.perform(get("/properties/" + home).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isNotFound());
	}

	@Test
	void aRentRecordIsChangedOnlyByWhoAddedItAndTheStartingRentOnTheTenancy() throws Exception {
		User tenant = tenant("Rent Tenant");
		JsonNode added = addHome(tenant, "G-707", TODAY.minusMonths(10));
		String id = added.at("/tenancy/id").asString();
		JsonNode records = json.readTree(mvc.perform(get("/tenancies/" + id + "/rent-records")
			.header("Authorization", support.bearer(tenant))).andReturn().getResponse().getContentAsString());
		assertThat(records.get(0).get("canEdit").asBoolean()).isFalse();
		mvc.perform(patch("/rent-records/" + records.get(0).get("id").asString())
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"%s\", \"monthlyRentInr\": 1}".formatted(TODAY.minusMonths(10))))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("starting_rent"));

		String raise = json.readTree(mvc.perform(post("/tenancies/" + id + "/rent-records")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"%s\", \"monthlyRentInr\": 33000}".formatted(TODAY.minusMonths(2))))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
		mvc.perform(patch("/rent-records/" + raise).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"%s\", \"monthlyRentInr\": 34000}".formatted(TODAY.minusMonths(1))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.monthlyRentInr").value(34000))
			.andExpect(jsonPath("$.canEdit").value(true));
		// The tenancy cannot start after a rent change any more.
		patchJson(tenant, "/tenancies/" + id, tenancyBody(TODAY.withDayOfMonth(1), null, 30000L, 90000L))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("after_rent_record"));
		mvc.perform(delete("/rent-records/" + raise).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isNoContent());
		JsonNode rows = history(admin(), "TENANCY", id);
		assertThat(rows.get(0).get("change").asString()).isEqualTo("DELETED");
		assertThat(rows.findValuesAsString("field")).contains("rent from " + TODAY.minusMonths(2).toString()
			.substring(0, 7) + ": monthlyRentInr");
	}

	@Test
	void aRecordAddedInTheStartMonthOfATenancyWithoutRentSurvivesItsEdit() throws Exception {
		User tenant = tenant("No Rent Tenant");
		LocalDate start = TODAY.minusMonths(5);
		JsonNode added = json.readTree(mvc.perform(post("/me/homes").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"home": {"city": "%s", "pincode": "300301", "unitLabel": "H-808", "propertyType": "APARTMENT",
					  "homeSize": "BHK2", "location": {"areaId": "%s", "societyId": "%s"}},
					 "relation": "RENT", "startDate": "%s"}"""
					.formatted(CITY, area.getPublicId(), society.getPublicId(), start)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		String id = added.at("/tenancy/id").asString();
		mvc.perform(post("/tenancies/" + id + "/rent-records").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"%s\", \"monthlyRentInr\": 27000}".formatted(start.withDayOfMonth(1))))
			.andExpect(status().isCreated());

		patchJson(tenant, "/tenancies/" + id, tenancyBody(start, null, null, 50000L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.depositInr").value(50000));
		mvc.perform(get("/tenancies/" + id + "/rent-records").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].monthlyRentInr").value(27000))
			.andExpect(jsonPath("$[0].canEdit").value(true));
		patchJson(tenant, "/tenancies/" + id, tenancyBody(start, null, 29000L, 50000L))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("after_rent_record"));
	}

	@Test
	void switchingAnOwnRecordToOwningMakesTheHomeTheirsAndCancelsTheTenancy() throws Exception {
		User tenant = tenant("Switch Tenant");
		JsonNode added = addHome(tenant, "J-909", TODAY.minusMonths(6));
		String id = added.at("/tenancy/id").asString();
		String home = added.at("/property/id").asString();

		JsonNode view = json.readTree(patchJson(tenant, "/tenancies/" + id, """
				{"startDate": "%s", "endDate": null, "initialRentInr": 30000, "depositInr": 90000, "relation": "OWN"}"""
				.formatted(TODAY.minusMonths(6)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andReturn().getResponse().getContentAsString());
		String token = view.get("accessToken").asString();
		assertThat(token).isNotBlank();

		Property row = propertyRows.findByPublicIdAndDeletedAtIsNull(UUID.fromString(home)).orElseThrow();
		assertThat(row.getLandlord().getId()).isEqualTo(tenant.getId());
		assertThat(row.getAddedBy()).isNull();
		assertThat(row.getUnitLabelVisibility()).isEqualTo(com.rentrix.rentrixserver.property.FieldVisibility.MASKED);
		assertThat(row.getVerificationStatus()).isEqualTo(VerificationState.UNVERIFIED);
		mvc.perform(get("/properties/" + home).header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.editable").value(true));
		User admin = admin();
		assertThat(history(admin, "PROPERTY", home).findValuesAsString("field")).contains("relation");
		assertThat(history(admin, "TENANCY", id).findValuesAsString("field")).contains("relation");
	}

	@Test
	void aRoleSwitchOnSomeoneElsesHomeIsRefused() throws Exception {
		User first = tenant("Home Adder");
		JsonNode added = addHome(first, "K-110", TODAY.minusYears(2));
		String home = added.at("/property/id").asString();
		patchJson(first, "/tenancies/" + added.at("/tenancy/id").asString(),
			tenancyBody(TODAY.minusYears(2), TODAY.minusMonths(8), 30000L, 90000L)).andExpect(status().isOk());
		User second = tenant("Later Tenant");
		String later = json.readTree(mvc.perform(post("/me/homes").header("Authorization", support.bearer(second))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"propertyId": "%s", "relation": "RENT", "startDate": "%s", "monthlyRentInr": 33000}"""
					.formatted(home, TODAY.minusMonths(5))))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).at("/tenancy/id").asString();

		patchJson(second, "/tenancies/" + later, """
				{"startDate": "%s", "initialRentInr": 33000, "relation": "OWN"}""".formatted(TODAY.minusMonths(5)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("role_change_not_allowed"));
		assertThat(tenancyRows.findByPublicId(UUID.fromString(later)).orElseThrow().getStatus().name())
			.isEqualTo("ACTIVE");
	}

	@Test
	void theCreatorAsksOurTeamToChangeABlockedItem() throws Exception {
		User tenant = tenant("Request Tenant");
		JsonNode added = addHome(tenant, "L-120", TODAY.minusMonths(3));
		String home = added.at("/property/id").asString();
		User stranger = tenant("Request Stranger");
		String body = """
			{"targetType": "PROPERTY", "targetId": "%s", "note": "Please fix the tower name."}""".formatted(home);

		mvc.perform(post("/me/change-requests").header("Authorization", support.bearer(stranger))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_creator"));
		String request = json.readTree(mvc.perform(post("/me/change-requests")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
		mvc.perform(post("/me/change-requests").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"targetType": "TENANCY", "targetId": "%s", "note": ""}""".formatted(added.at("/tenancy/id").asString())))
			.andExpect(status().isUnprocessableContent());

		User admin = admin();
		mvc.perform(get("/admin/queue").with(adminHost()).header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin))
				.param("type", "REPORT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '%s')].title".formatted(request)).value("Change request"))
			.andExpect(jsonPath("$.items[?(@.id == '%s')].details[1]".formatted(request))
				.value("Please fix the tower name."))
			.andExpect(jsonPath("$.items[?(@.id == '%s')].actions[0]".formatted(request)).value("APPROVE"));
		mvc.perform(post("/admin/reports/" + request + "/resolve").with(adminHost())
				.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"outcome\": \"DISMISS\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void aRepeatChangeRequestJoinsTheOpenOneAndReopensAHandledOne() throws Exception {
		User tenant = tenant("Repeat Request Tenant");
		String tenancy = addHome(tenant, "M-130", TODAY.minusMonths(3)).at("/tenancy/id").asString();
		String body = """
			{"targetType": "TENANCY", "targetId": "%s", "note": "%s"}""";
		String first = json.readTree(mvc.perform(post("/me/change-requests")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON)
				.content(body.formatted(tenancy, "Please remove this tenancy.")))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
		mvc.perform(post("/me/change-requests").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body.formatted(tenancy, "Also the rent record of last month.")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(first));
		assertThat(jdbc.queryForObject("select details from reports where public_id = ?", String.class, first))
			.isEqualTo("Please remove this tenancy.\n\nAlso the rent record of last month.");

		User admin = admin();
		mvc.perform(post("/admin/reports/" + first + "/resolve").with(adminHost())
				.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"outcome\": \"DISMISS\"}"))
			.andExpect(status().isOk());
		mvc.perform(post("/me/change-requests").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content(body.formatted(tenancy, "Asking again.")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(first));
		mvc.perform(get("/admin/queue").with(adminHost()).header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin))
				.param("type", "REPORT"))
			.andExpect(jsonPath("$.items[?(@.id == '%s')].details[1]".formatted(first)).value("Asking again."));
	}

	@Test
	void theHistoryNeverHoldsAFullIdNumber() {
		assertThat(EditHistory.mask("my aadhaar 1234 5678 9012 and PAN ABCDE1234F"))
			.isEqualTo("my aadhaar XXXX XXXX 9012 and PAN XXXXXXXXXX");
		assertThat(EditHistory.mask("123456789012")).isEqualTo("XXXX XXXX 9012");
		assertThat(EditHistory.mask("rent 30000")).isEqualTo("rent 30000");
	}

}
