package com.rentrix.rentrixserver.home;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationTestSupport;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.RecordingEmailSender;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Add your home" (D-139, D-140): a tenant with a verified phone adds the home they rent while its landlord is not on
 * the platform; it is published at once without the green tick, their tenancy stands at once, its rent shows
 * unconfirmed, the home can be reviewed, and the owner contact they typed stays private.
 */
@IntegrationTest
@Transactional
class HomeApiTest {

	private static final String CITY = "ZQH";
	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
	private static final String GOOD = "Bright flat, quick repairs and a fair deposit return; the lifts work well too.";

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
	CityRepository cities;
	
	@Autowired
	RecordingEmailSender mail;

	private Area area;
	private Society society;
	private SocietyTower tower;

	@BeforeEach
	void places() {
		support.city(CITY, "Home Test City", true);
		area = locations.area(CITY, "Home Area", "300201", "300202");
		society = locations.society(area, "Lakeside Habitat");
		tower = locations.tower(society, "Tower 4", 0, "Wing A");
	}

	private User tenant(String name) {
		User user = data.verified(name, Role.TENANT);
		data.verifyPhone(user);
		return user;
	}

	private String newHome(String unit) {
		return """
			{"city": "%s", "pincode": "300201", "unitLabel": "%s", "propertyType": "APARTMENT", "bedrooms": 2,
			 "location": {"areaId": "%s", "societyId": "%s", "towerId": "%s"}}""".formatted(CITY, unit,
			area.getPublicId(), society.getPublicId(), tower.getPublicId());
	}

	private String rentBody(String home, LocalDate start) {
		return """
			{"home": %s, "relation": "RENT", "startDate": "%s", "monthlyRentInr": 42000, "depositInr": 150000,
			 "owner": {"name": "Ravi Owner", "email": "ravi.owner@example.test", "phone": "+919800000001"}}"""
			.formatted(home, start);
	}

	private ResultActions add(User user, String body) throws Exception {
		return mvc.perform(post("/me/homes").header("Authorization", support.bearer(user))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode added(User user, String body) throws Exception {
		return json.readTree(add(user, body).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString());
	}

	@Test
	void aTenantAddsTheirHomeAndTenancyAtOnceNotVerifiedYet() throws Exception {
		User tenant = tenant("Asha Tenant");
		JsonNode result = added(tenant, rentBody(newHome("Flat 1203"), TODAY.minusMonths(8)));

		JsonNode property = result.get("property");
		assertThat(property.get("status").asString()).isEqualTo("PUBLISHED");
		assertThat(property.get("addedByTenant").asBoolean()).isTrue();
		assertThat(property.get("verification").asString()).isEqualTo("UNVERIFIED");
		assertThat(property.at("/landlord/shortName").asString()).isEqualTo(UnclaimedLandlord.PUBLIC_NAME);
		assertThat(property.at("/landlord/id").isMissingNode()).isTrue();
		assertThat(property.at("/location/society/name").asString()).isEqualTo("Lakeside Habitat");
		// The tenant who typed it sees the owner contact; nobody else does (D-140).
		assertThat(property.at("/ownerContact/email").asString()).isEqualTo("ravi.owner@example.test");

		JsonNode tenancy = result.get("tenancy");
		assertThat(tenancy.get("status").asString()).isEqualTo("ACTIVE");
		assertThat(tenancy.at("/landlord/shortName").asString()).isEqualTo(UnclaimedLandlord.PUBLIC_NAME);
		assertThat(result.has("accessToken")).isFalse();

		String id = property.get("id").asString();
		User other = tenant("Other Reader");
		mvc.perform(get("/properties/" + id).header("Authorization", support.bearer(other)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unitLabel").value("Flat 1203"))
			.andExpect(jsonPath("$.unitLabelPublic").value(true))
			.andExpect(jsonPath("$.ownerContact").doesNotExist());
		assertThat(mail.to(UnclaimedLandlord.EMAIL)).isEmpty();
		// Its rent shows at once, marked unconfirmed (nobody can confirm it yet).
		mvc.perform(get("/properties/" + id + "/rent-history").header("Authorization", support.bearer(other)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows[0].monthlyRentInr").value(42000))
			.andExpect(jsonPath("$.rows[0].confirmed").value(false));
		// Searchable at once.
		mvc.perform(get("/properties").param("city", CITY).header("Authorization", support.bearer(other)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(id))
			.andExpect(jsonPath("$.items[0].landlordName").value(UnclaimedLandlord.PUBLIC_NAME));

		// The home can be reviewed at once (window D-39 permitting); the absent landlord cannot.
		String tenancyId = tenancy.get("id").asString();
		// "Rate and review" stays an open next step until the tenant writes one (captain 2026-11-23 14:53).
		assertThat(tenancy.get("reviewed").asBoolean()).isFalse();
		String reviewId = json.readTree(mvc.perform(post("/tenancies/" + tenancyId + "/reviews")
				.header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\": \"TENANT_TO_PROPERTY\", \"ratingOverall\": 4, \"body\": \"" + GOOD + "\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.verification").value("UNVERIFIED"))
			.andReturn().getResponse().getContentAsString()).get("id").asString();
		mvc.perform(post("/tenancies/" + tenancyId + "/reviews").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\": \"TENANT_TO_LANDLORD\", \"ratingOverall\": 4, \"body\": \"" + GOOD + "\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("landlord_not_on_platform"));
		mvc.perform(get("/tenancies/" + tenancyId).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewed").value(true))
			.andExpect(jsonPath("$.homeReviewStatus").value("PUBLISHED"));
		// A withdrawn review leaves the home's page, so "See my review" has nothing to open; it cannot be rewritten.
		mvc.perform(delete("/reviews/" + reviewId).header("Authorization", support.bearer(tenant)))
			.andExpect(status().is2xxSuccessful());
		mvc.perform(get("/tenancies/" + tenancyId).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewed").value(true))
			.andExpect(jsonPath("$.homeReviewStatus").value("WITHDRAWN"));
	}

	@Test
	void theSameFlatIsOfferedBackBeforeADuplicateIsAdded() throws Exception {
		User first = tenant("First Tenant");
		// A whole home has one tenancy at a time (D-34 overlap guard), so the first one has ended.
		String id = added(first, rentBody(newHome("C-1204"), TODAY.minusYears(2))
			.replace("\"relation\"", "\"endDate\": \"%s\", \"relation\"".formatted(TODAY.minusMonths(6))))
			.at("/property/id").asString();

		User second = tenant("Second Tenant");
		mvc.perform(get("/me/homes/matches").header("Authorization", support.bearer(second))
				.param("city", CITY).param("pincode", "300201").param("society", society.getPublicId().toString())
				.param("unit", "flat c 1204"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(id));
		mvc.perform(get("/me/homes/matches").header("Authorization", support.bearer(second))
				.param("city", CITY).param("pincode", "300201").param("societyName", "Lakeside Habitat")
				.param("unit", "C-1205"))
			.andExpect(jsonPath("$.length()").value(0));

		// A listing's flat number is MASKED (D-57): it matches on the society alone, whatever flat is typed.
		User owner = tenant("Masked Owner");
		String masked = added(owner, """
			{"home": %s, "relation": "OWN"}""".formatted(newHome("Flat 9901"))).at("/property/id").asString();
		for (String unit : new String[] {"9901", "1"}) {
			mvc.perform(get("/me/homes/matches").header("Authorization", support.bearer(second))
					.param("city", CITY).param("pincode", "300201").param("society", society.getPublicId().toString())
					.param("unit", unit))
				.andExpect(jsonPath("$[?(@.id == '%s')]".formatted(masked)).isNotEmpty())
				.andExpect(jsonPath("$[0].unitLabel").doesNotExist());
		}
		mvc.perform(get("/me/homes/matches").header("Authorization", support.bearer(second))
				.param("city", CITY).param("pincode", "300201").param("unit", "9901"))
			.andExpect(jsonPath("$.length()").value(0));

		// "Is it this one?" → yes: the second tenancy goes on the same home.
		String picked = """
			{"propertyId": "%s", "relation": "RENT", "startDate": "%s", "monthlyRentInr": 45000}"""
			.formatted(id, TODAY.minusMonths(3));
		assertThat(added(second, picked).at("/property/id").asString()).isEqualTo(id);
	}

	@Test
	void aTenantsOwnRecordOnAListedHomeNeverWaitsForTheOwner() throws Exception {
		User landlord = data.approved("Listed Landlord", Role.LANDLORD);
		Property listed = data.property(landlord, CITY, "Home Area", PropertyStatus.PUBLISHED);
		User tenant = tenant("Listed Tenant");
		JsonNode result = added(tenant, """
			{"propertyId": "%s", "relation": "RENT", "startDate": "%s", "monthlyRentInr": 30000}"""
			.formatted(listed.getPublicId(), TODAY.minusMonths(5)));
		// D-142: owner confirmation is optional, so the tenant's period stands at once.
		assertThat(result.at("/tenancy/status").asString()).isEqualTo("ACTIVE");
		assertThat(result.at("/property/addedByTenant").asBoolean()).isFalse();
		mvc.perform(get("/properties/" + listed.getPublicId() + "/rent-history")
				.header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.rows[0].monthlyRentInr").value(30000))
			.andExpect(jsonPath("$.rows[0].confirmed").value(false));
		assertThat(mail.last(landlord.getEmail()).subject()).isEqualTo("A tenant recorded a tenancy on your home");

		// The one-sided record holds no dates against the landlord's own tenancies (D-142).
		User real = data.approved("Real Tenant", Role.TENANT);
		String invite = """
			{"propertyId": "%s", "counterpartyEmail": "%s", "startDate": "%s", "endDate": "%s", "initialRentInr": 28000}"""
			.formatted(listed.getPublicId(), real.getEmail(), TODAY.minusYears(1), TODAY.minusMonths(1));
		String id = json.readTree(mvc.perform(post("/tenancies").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(invite))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
		mvc.perform(post("/tenancies/" + id + "/confirm").header("Authorization", support.bearer(real)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENDED"));
		// Its rent row keeps its own months; the one-sided row is listed next to it, unconfirmed.
		mvc.perform(get("/properties/" + listed.getPublicId() + "/rent-history")
				.header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.rows.length()").value(2))
			.andExpect(jsonPath("$.rows[0].fromMonth").value(YearMonth.from(TODAY.minusYears(1)).toString()))
			.andExpect(jsonPath("$.rows[0].toMonth").value(YearMonth.from(TODAY.minusMonths(1)).toString()))
			.andExpect(jsonPath("$.rows[0].monthlyRentInr").value(28000))
			.andExpect(jsonPath("$.rows[0].confirmed").value(true))
			.andExpect(jsonPath("$.rows[1].fromMonth").value(YearMonth.from(TODAY.minusMonths(5)).toString()))
			.andExpect(jsonPath("$.rows[1].toMonth").doesNotExist())
			.andExpect(jsonPath("$.rows[1].confirmed").value(false));
	}
	
	@Test
	void aHomeInACityNotInTheListCreatesThatCityPending() throws Exception {
		User tenant = tenant("Far Tenant");
		String body = """
			{"home": {"city": "OTHER", "pincode": "570002", "locality": "Gokulam", "unitLabel": "12",
			          "propertyType": "INDEPENDENT_HOUSE"},
			 "newCity": {"name": " Zq Mysuru ", "state": "Karnataka"},
			 "relation": "RENT", "startDate": "%s", "monthlyRentInr": 18000}""".formatted(TODAY.minusMonths(4));
		JsonNode first = added(tenant, body);
		assertThat(first.at("/property/city/name").asString()).isEqualTo("Zq Mysuru");
		String code = first.at("/property/city/code").asString();
		// D-143: pending until an admin sets it live, so it is not offered in the city list yet.
		assertThat(cities.findById(code)).get().extracting(City::isLive).isEqualTo(false);
		mvc.perform(get("/cities")).andExpect(jsonPath("$[?(@.code == '%s')]".formatted(code)).isEmpty());
		
		// The same name again is the same city, never a second row.
		User second = tenant("Far Owner");
		JsonNode again = added(second, body.replace("\"RENT\", \"startDate\": \"%s\", \"monthlyRentInr\": 18000"
			.formatted(TODAY.minusMonths(4)), "\"OWN\"").replace("Zq Mysuru", "ZQ MYSURU"));
		assertThat(again.at("/property/city/code").asString()).isEqualTo(code);
		
		add(tenant, body.replace("\"locality\": \"Gokulam\",",
				"\"location\": {\"newArea\": {\"name\": \"Gokulam\", \"pincode\": \"570002\"}},"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("home.location"));
	}
	
	@Test
	void anOwnerAddsTheirHomeAndBecomesALandlord() throws Exception {
		User owner = tenant("Owner Tenant");
		JsonNode result = added(owner, """
			{"home": %s, "relation": "OWN"}""".formatted(newHome("Villa 7")));
		assertThat(result.at("/property/addedByTenant").asBoolean()).isFalse();
		assertThat(result.at("/property/landlord/shortName").asString()).isEqualTo("Owner T.");
		assertThat(result.has("tenancy")).isFalse();
		assertThat(result.get("accessToken").asString()).isNotBlank();
		// An owner's flat number stays MASKED like any listing (D-57, D-140): others do not see it.
		User other = tenant("Owner Reader");
		mvc.perform(get("/properties/" + result.at("/property/id").asString())
				.header("Authorization", support.bearer(other)))
			.andExpect(jsonPath("$.unitLabel").doesNotExist())
			.andExpect(jsonPath("$.unitLabelPublic").value(false));

		add(owner, """
			{"propertyId": "%s", "relation": "OWN"}""".formatted(result.at("/property/id").asString()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("claim_not_available"));
	}

	@Test
	void addingAHomeNeedsNoVerificationButTheTenancyFields() throws Exception {
		// No phone, Aadhaar or LinkedIn verified: posting is open to every signed-in user (D-163).
		User noPhone = data.verified("No Phone", Role.TENANT);
		add(noPhone, rentBody(newHome("Flat 1"), TODAY.minusMonths(8)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.property.verification").value("UNVERIFIED"));

		User tenant = tenant("Half Form");
		add(tenant, """
			{"home": %s, "relation": "RENT"}""".formatted(newHome("Flat 2")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("startDate"));
	}

	/** Captain 2026-11-23 09:55: every optional field may stay blank, the rent too ("Rent not shared"). */
	@Test
	void aHomeSavesWithTheOwnerNameOnlyAndNoRent() throws Exception {
		User tenant = tenant("No Rent Tenant");
		JsonNode result = added(tenant, """
			{"home": %s, "relation": "RENT", "startDate": "%s", "owner": {"name": "Ravi Owner", "email": "", "phone": " "}}"""
			.formatted(newHome("Flat 9"), TODAY.minusMonths(3)));
		JsonNode rent = result.at("/tenancy/initialRentInr");
		assertThat(rent.isNull() || rent.isMissingNode()).isTrue();
		assertThat(result.at("/property/ownerContact/name").asString()).isEqualTo("Ravi Owner");
		assertThat(result.at("/property/ownerContact/email").isMissingNode()).isTrue();
		String id = result.at("/property/id").asString();
		mvc.perform(get("/properties/" + id + "/rent-history").header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows").isEmpty());
	}

	/** #53 click-test bugs 1 and 2: a one-letter wing or tower saves, and the new names show at once, tagged pending. */
	@Test
	void oneLetterTowerAndWingSaveAndShowAtOnceTaggedPending() throws Exception {
		User tenant = tenant("Wing Tenant");
		String home = """
			{"city": "%s", "pincode": "300201", "unitLabel": "1204", "propertyType": "APARTMENT",
			 "location": {"areaId": "%s", "newSociety": {"name": "Aparna Lake View", "kind": "APARTMENT_COMPLEX"},
			 "newTower": {"name": "4", "kind": "TOWER"}, "newWing": {"name": "B"}}}""".formatted(CITY,
			area.getPublicId());
		JsonNode property = added(tenant, rentBody(home, TODAY.minusMonths(8))).get("property");
		assertThat(property.at("/location/tower/name").asString()).isEqualTo("4");
		assertThat(property.at("/location/wing/name").asString()).isEqualTo("B");
		assertThat(property.at("/location/wing/status").asString()).isEqualTo("PENDING");

		// Open by default (R2 / R8): anonymous search lists it at once, with the pending tag.
		mvc.perform(get("/properties").param("city", CITY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '%s')].locationPending".formatted(property.get("id").asString()))
				.value(true));

		add(tenant, rentBody("""
			{"city": "%s", "pincode": "300201", "unitLabel": "1205", "propertyType": "APARTMENT",
			 "location": {"areaId": "%s", "societyId": "%s", "towerId": "%s", "newWing": {"name": "-"}}}"""
			.formatted(CITY, area.getPublicId(), society.getPublicId(), tower.getPublicId()), TODAY.minusMonths(8)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("location.newWing.name"));
	}

	/** Captain 2026-11-23 11:35: a standalone apartment needs its building name; a house does not. */
	@Test
	void aStandaloneApartmentNeedsItsBuildingName() throws Exception {
		User tenant = tenant("Standalone Tenant");
		String standalone = """
			{"city": "%s", "pincode": "300201", "unitLabel": "C202", "propertyType": "%s"%s,
			 "location": {"areaId": "%s"}}""";
		add(tenant, rentBody(standalone.formatted(CITY, "APARTMENT", "", area.getPublicId()), TODAY.minusMonths(4)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("home.buildingName"));
		JsonNode flat = added(tenant, rentBody(standalone.formatted(CITY, "APARTMENT", ", \"buildingName\": \"Sai Residency\"",
			area.getPublicId()), TODAY.minusMonths(4))).get("property");
		assertThat(flat.get("buildingName").asString()).isEqualTo("Sai Residency");
		User other = tenant("House Tenant");
		added(other, rentBody(standalone.formatted(CITY, "INDEPENDENT_HOUSE", "", area.getPublicId()).replace("C202", "12"),
			TODAY.minusMonths(4)));
	}

	/**
	 * Captain 2026-11-23 17:18: the home size is optional, fills the bedrooms, shows on the page and filters
	 * search; "Other" keeps the user's words; a typed society name is a new one, pending review.
	 */
	@Test
	void theHomeSizeSavesShowsAndFiltersSearch() throws Exception {
		User tenant = tenant("Size Tenant");
		String sized = """
			{"city": "%s", "pincode": "300201", "unitLabel": "%s", "propertyType": "APARTMENT", "homeSize": "%s"%s,
			 "location": {"areaId": "%s", "newSociety": {"name": "%s", "kind": "APARTMENT_COMPLEX"}}}""";
		JsonNode rk = added(tenant, rentBody(sized.formatted(CITY, "R1", "RK1", "", area.getPublicId(),
			"Typed Society Heights"), TODAY.minusMonths(4))).get("property");
		assertThat(rk.get("homeSize").asString()).isEqualTo("RK1");
		assertThat(rk.get("bedrooms").asInt()).isZero();
		assertThat(rk.at("/location/society/status").asString()).isEqualTo("PENDING");
		JsonNode two = added(tenant("Size Tenant Two"), rentBody(sized.formatted(CITY, "B2", "BHK2", "",
			area.getPublicId(), "Typed Society Two"), TODAY.minusMonths(4))).get("property");
		assertThat(two.get("bedrooms").asInt()).isEqualTo(2);
		JsonNode other = added(tenant("Size Tenant Three"), rentBody(sized.formatted(CITY, "O3", "OTHER",
			", \"homeSizeOther\": \" 2.5 BHK \"", area.getPublicId(), "Typed Society Three"), TODAY.minusMonths(4)))
			.get("property");
		assertThat(other.get("homeSizeOther").asString()).isEqualTo("2.5 BHK");
		assertThat(other.get("bedrooms").isNull()).isTrue();

		mvc.perform(get("/properties/" + two.get("id").asString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.homeSize").value("BHK2"));
		mvc.perform(get("/properties").param("city", CITY).param("homeSize", "bhk2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(two.get("id").asString())))
			.andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.hasItem(rk.get("id").asString()))));
		mvc.perform(get("/properties").param("city", CITY).param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '%s')].homeSize".formatted(rk.get("id").asString()))
				.value(org.hamcrest.Matchers.contains("RK1")))
			.andExpect(jsonPath("$.items[?(@.id == '%s')].homeSize".formatted(other.get("id").asString()))
				.value(org.hamcrest.Matchers.contains("OTHER")))
			.andExpect(jsonPath("$.items[?(@.id == '%s')].homeSizeOther".formatted(other.get("id").asString()))
				.value(org.hamcrest.Matchers.contains("2.5 BHK")))
			.andExpect(jsonPath("$.items[?(@.id == '%s')].homeSizeOther".formatted(rk.get("id").asString()))
				.value(org.hamcrest.Matchers.empty()));
		add(tenant("Size Tenant Four"), rentBody(sized.formatted(CITY, "O4", "OTHER", ", \"homeSizeOther\": \"  \"",
			area.getPublicId(), "Typed Society Four"), TODAY.minusMonths(4)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("homeSizeOther"));
		mvc.perform(get("/properties").param("homeSize", "3BHK"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("homeSize"));
	}

	/** Firstmate 2026-11-23 14:50: PIN vs city blocks at step 1; PIN vs area only warns, and the home saves. */
	@Test
	void stepOneChecksThePinAgainstTheCityAndOnlyWarnsAboutTheArea() throws Exception {
		User tenant = tenant("Pin Tenant");
		mvc.perform(get("/me/homes/address-check").param("city", "BLR").param("pincode", "400001")
				.header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pincodeError").value("This pincode is not in Bangalore."))
			.andExpect(jsonPath("$.pincodeWarning").doesNotExist());
		mvc.perform(get("/me/homes/address-check").param("city", CITY).param("pincode", "300299")
				.param("area", area.getPublicId().toString()).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pincodeError").doesNotExist())
			.andExpect(jsonPath("$.pincodeWarning").value("300299 is usually not in Home Area; check the PIN."))
			.andExpect(jsonPath("$.areaPincodes[0]").value("300201"));
		mvc.perform(get("/me/homes/address-check").param("city", CITY).param("pincode", "300202")
				.param("area", area.getPublicId().toString()).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.pincodeWarning").doesNotExist());
		// The user kept the PIN: the home saves on its area.
		JsonNode kept = added(tenant, rentBody(newHome("Flat 77").replace("\"300201\"", "\"300299\""),
			TODAY.minusMonths(2))).get("property");
		assertThat(kept.get("pincode").asString()).isEqualTo("300299");
	}

}
