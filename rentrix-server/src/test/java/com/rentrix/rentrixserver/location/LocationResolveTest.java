package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link LocationService#resolve}: the D-110 placement rules, the PIN-code-in-area check (D-108) and inline
 * proposals saved as {@code PENDING} rows owned by the proposer (D-109). Each case runs in a rolled-back transaction.
 */
@IntegrationTest
@Transactional
class LocationResolveTest {
	
	private static final String CITY = "ZQR";
	private static final String OTHER_CITY = "ZQT";
	
	@Autowired
	LocationService service;
	
	@Autowired
	LocationTestSupport locations;
	
	@Autowired
	PropertyTestSupport support;
	
	@Autowired
	TestData data;
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	AreaRepository areas;
	
	@Autowired
	SocietyRepository societies;
	
	@Autowired
	SocietyTowerRepository towers;
	
	private User landlord;
	private Area area;
	private Society society;
	private SocietyTower tower1;
	private SocietyTower tower2;
	
	@BeforeEach
	void hierarchy() {
		support.city(CITY, "Resolve Test City", true);
		support.city(OTHER_CITY, "Other Test City", true);
		landlord = data.user("Resolve Landlord", Role.LANDLORD);
		area = locations.area(CITY, "Resolve Area", "200001", "200002");
		society = locations.society(area, "Resolve Greens");
		tower1 = locations.tower(society, "Tower 1", 0, "Wing A", "Wing B");
		tower2 = locations.tower(society, "Tower 2", 1, "Wing A");
	}
	
	private static LocationInput place(Area area, Society society, SocietyTower tower, SocietyWing wing) {
		return LocationInput.of(id(area), id(society), id(tower), id(wing));
	}
	
	private static UUID id(com.rentrix.rentrixserver.common.persistence.PublicEntity row) {
		return row == null ? null : row.getPublicId();
	}
	
	private static ApiException rejected(ThrowableAssert.ThrowingCallable call) {
		ApiException e = catchThrowableOfType(ApiException.class, call);
		assertThat(e).as("ApiException").isNotNull();
		return e;
	}
	
	@Test
	void acceptsStandaloneAndEveryLevelOfASociety() {
		Placement standalone = service.resolve(CITY, "200002", place(area, null, null, null), landlord);
		assertThat(standalone.standalone()).isTrue();
		assertThat(standalone.area()).isEqualTo(area);
		
		SocietyWing wingB = LocationTestSupport.wing(tower1, "Wing B");
		Placement full = service.resolve(CITY, "200001", place(area, society, tower1, wingB), landlord);
		assertThat(full.standalone()).isFalse();
		assertThat(full.society()).isEqualTo(society);
		assertThat(full.tower()).isEqualTo(tower1);
		assertThat(full.wing()).isEqualTo(wingB);
		
		// a society without a tower, and a tower without a wing, are both fine (D-107)
		assertThat(service.resolve(CITY, "200001", place(area, society, null, null), landlord).tower()).isNull();
		assertThat(service.resolve(CITY, "200001", place(area, society, tower2, null), landlord).wing()).isNull();
	}
	
	@Test
	void appliesEachPlacementRule() {
		SocietyWing wingA = LocationTestSupport.wing(tower1, "Wing A");
		Area otherArea = locations.area(CITY, "Other Area", "200001");
		Society otherSociety = locations.society(otherArea, "Other Greens");
		SocietyTower otherTower = locations.tower(society, "Tower 3", 2, "Wing C");
		Area elsewhere = locations.area(OTHER_CITY, "Elsewhere", "200001");
		
		assertThat(rejected(() -> service.resolve(CITY, "200001", place(null, null, null, null), landlord)))
			.satisfies(e -> assertThat(e.getErrors().getFirst().field()).isEqualTo("location.areaId"));
		assertMismatch(() -> service.resolve(CITY, "200001", place(area, society, null, wingA), landlord),
			"location.wingId");
		assertMismatch(() -> service.resolve(CITY, "200001", place(area, null, tower1, null), landlord),
			"location.towerId");
		assertMismatch(() -> service.resolve(CITY, "200001",
			place(area, society, otherTower, wingA), landlord), "location.wingId");
		assertMismatch(() -> service.resolve(CITY, "200001",
			place(otherArea, otherSociety, tower1, null), landlord), "location.towerId");
		assertMismatch(() -> service.resolve(CITY, "200001", place(otherArea, society, null, null), landlord),
			"location.societyId");
		assertMismatch(() -> service.resolve(CITY, "200001", place(elsewhere, null, null, null), landlord),
			"location.areaId");
		
		ApiException notInArea = rejected(() -> service.resolve(CITY, "200009", place(area, null, null, null),
			landlord));
		assertThat(notInArea.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(notInArea.getCode()).isEqualTo("pincode_not_in_area");
		assertThat(rejected(() -> service.resolve(CITY, "2000", place(area, null, null, null), landlord)).getCode())
			.isEqualTo("validation_failed");
		assertThat(rejected(() -> service.resolve("NOPE", "200001", place(area, null, null, null), landlord))
			.getCode()).isEqualTo("city_not_live");
		ApiException both = rejected(() -> service.resolve(CITY, "200001", new LocationInput(area.getPublicId(),
			new LocationInput.NewArea("Fresh", "200001"), null, null, null, null, null, null), landlord));
		assertThat(both.getErrors().getFirst().code()).isEqualTo("one_of");
		ApiException unknown = rejected(() -> service.resolve(CITY, "200001",
			LocationInput.of(UUID.randomUUID(), null, null, null), landlord));
		assertThat(unknown.getErrors().getFirst().code()).isEqualTo("not_found");
	}
	
	private static void assertMismatch(ThrowableAssert.ThrowingCallable call, String field) {
		ApiException e = rejected(call);
		assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(e.getCode()).isEqualTo("location_mismatch");
		assertThat(e.getErrors().getFirst().field()).isEqualTo(field);
	}
	
	@Test
	void savesProposalsAsPendingRowsOwnedByTheProposer() throws Exception {
		LocationInput input = new LocationInput(area.getPublicId(), null, null,
			new LocationInput.NewSociety("  New   Heights ", SocietyKind.APARTMENT_COMPLEX, "Demo Dev", null), null,
			new LocationInput.NewTower("Block A", TowerKind.BLOCK), null, new LocationInput.NewWing("Wing 1"));
		Placement placed = service.resolve(CITY, "200001", input, landlord);
		assertThat(placed.society().getName()).isEqualTo("New Heights");
		assertThat(placed.society().getStatus()).isEqualTo(LocationStatus.PENDING);
		assertThat(placed.society().getCreatedBy()).isEqualTo(landlord);
		assertThat(placed.society().getCity().getCode()).isEqualTo(CITY);
		assertThat(placed.tower().getStatus()).isEqualTo(LocationStatus.PENDING);
		assertThat(placed.tower().getKind()).isEqualTo(TowerKind.BLOCK);
		assertThat(placed.wing().getStatus()).isEqualTo(LocationStatus.PENDING);
		assertThat(placed.wing().getTower()).isEqualTo(placed.tower());
		assertThat(placed.wing().getId()).isNotNull();
		
		// the proposer sees and can reuse it; another landlord can neither see nor use it
		User other = data.user("Other Landlord", Role.LANDLORD);
		UUID proposed = placed.society().getPublicId();
		mvc.perform(get("/societies/{id}", proposed).header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.towers[0].wings[0].name").value("Wing 1"));
		mvc.perform(get("/societies/{id}", proposed).header("Authorization", support.bearer(other)))
			.andExpect(status().isNotFound());
		assertThat(service.resolve(CITY, "200001", LocationInput.of(area.getPublicId(), proposed, null, null),
			landlord).society().getPublicId()).isEqualTo(proposed);
		ApiException hidden = rejected(() -> service.resolve(CITY, "200001",
			LocationInput.of(area.getPublicId(), proposed, null, null), other));
		assertThat(hidden.getErrors().getFirst().field()).isEqualTo("location.societyId");
		assertThat(hidden.getErrors().getFirst().code()).isEqualTo("not_found");
	}
	
	@Test
	void proposesANewAreaWithThePropertyPincode() {
		Placement placed = service.resolve(CITY, "200050", new LocationInput(null,
			new LocationInput.NewArea("Fresh Layout", "200050"), null,
			new LocationInput.NewSociety("Fresh Greens", SocietyKind.GATED_COMMUNITY, null, null), null, null, null,
			null), landlord);
		Area fresh = areas.findByPublicId(placed.area().getPublicId()).orElseThrow();
		assertThat(fresh.getStatus()).isEqualTo(LocationStatus.PENDING);
		assertThat(fresh.getCreatedBy()).isEqualTo(landlord);
		assertThat(fresh.pincodeList()).containsExactly("200050");
		assertThat(placed.society().getArea()).isEqualTo(fresh);
		
		assertThat(rejected(() -> service.resolve(CITY, "200051", new LocationInput(null,
			new LocationInput.NewArea("Another Layout", "200050"), null, null, null, null, null, null), landlord))
			.getCode()).isEqualTo("pincode_not_in_area");
	}
	
	@Test
	void anAdminCreatesActiveRows() {
		User admin = data.user("Location Admin", Role.ADMIN, Role.LANDLORD);
		Placement placed = service.resolve(CITY, "200001", new LocationInput(area.getPublicId(), null,
			society.getPublicId(), null, null, new LocationInput.NewTower("Tower 7", null), null, null), admin);
		assertThat(placed.tower().getStatus()).isEqualTo(LocationStatus.ACTIVE);
		assertThat(placed.tower().getKind()).isEqualTo(TowerKind.TOWER);
		assertThat(placed.tower().getSortOrder()).isEqualTo(2);
	}
	
	@Test
	void namesAreUniquePerParent() {
		assertThat(area.getStatus()).isEqualTo(LocationStatus.ACTIVE);
		ApiException sameArea = rejected(() -> service.resolve(CITY, "200001", new LocationInput(null,
			new LocationInput.NewArea("resolve   AREA", "200001"), null, null, null, null, null, null), landlord));
		assertThat(sameArea.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(sameArea.getCode()).isEqualTo("location_exists");
		assertThat(rejected(() -> service.resolve(CITY, "200001", new LocationInput(area.getPublicId(), null, null,
			new LocationInput.NewSociety("RESOLVE GREENS", SocietyKind.GATED_COMMUNITY, null, null), null, null, null,
			null), landlord)).getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(rejected(() -> service.resolve(CITY, "200001", new LocationInput(area.getPublicId(), null,
			society.getPublicId(), null, null, new LocationInput.NewTower("tower 2", null), null, null), landlord))
			.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(rejected(() -> service.resolve(CITY, "200001", new LocationInput(area.getPublicId(), null,
			society.getPublicId(), null, tower1.getPublicId(), null, null, new LocationInput.NewWing("wing a")),
			landlord)).getStatus()).isEqualTo(HttpStatus.CONFLICT);
		// the same name under another parent is fine
		Area otherArea = locations.area(CITY, "Second Area", "200001");
		assertThat(service.resolve(CITY, "200001", new LocationInput(otherArea.getPublicId(), null, null,
			new LocationInput.NewSociety("Resolve Greens", SocietyKind.GATED_COMMUNITY, null, null), null, null, null,
			null), landlord).society().getStatus()).isEqualTo(LocationStatus.PENDING);
	}
	
	@Test
	void aSecondProposalOfTheSameNameJoinsThePendingRow() {
		Placement first = service.resolve(CITY, "200060", new LocationInput(null,
			new LocationInput.NewArea("Lake View", "200060"), null,
			new LocationInput.NewSociety("Sky Towers", SocietyKind.GATED_COMMUNITY, null, null), null,
			new LocationInput.NewTower("T1", null), null, null), landlord);
		long areaRows = areas.count();
		long societyRows = societies.count();
		long towerRows = towers.count();
		
		User other = data.user("Second Proposer", Role.LANDLORD);
		Placement second = service.resolve(CITY, "200060", new LocationInput(null,
			new LocationInput.NewArea("  lake   VIEW ", "200060"), null,
			new LocationInput.NewSociety("SKY towers", SocietyKind.APARTMENT_COMPLEX, null, null), null,
			new LocationInput.NewTower("t1", null), null, null), other);
		
		assertThat(second.area().getPublicId()).isEqualTo(first.area().getPublicId());
		assertThat(second.society().getPublicId()).isEqualTo(first.society().getPublicId());
		assertThat(second.tower().getPublicId()).isEqualTo(first.tower().getPublicId());
		assertThat(second.society().getStatus()).isEqualTo(LocationStatus.PENDING);
		assertThat(second.society().getCreatedBy()).isEqualTo(landlord);
		assertThat(areas.count()).isEqualTo(areaRows);
		assertThat(societies.count()).isEqualTo(societyRows);
		assertThat(towers.count()).isEqualTo(towerRows);
		
		// a society proposal under an existing area joins the same way
		Society proposed = service.resolve(CITY, "200001", new LocationInput(area.getPublicId(), null, null,
			new LocationInput.NewSociety("Palm Court", SocietyKind.GATED_COMMUNITY, null, null), null, null, null,
			null), landlord).society();
		assertThat(service.resolve(CITY, "200001", new LocationInput(area.getPublicId(), null, null,
			new LocationInput.NewSociety(" palm   COURT", SocietyKind.GATED_COMMUNITY, null, null), null, null, null,
			null), other).society().getPublicId()).isEqualTo(proposed.getPublicId());
		assertThat(societies.count()).isEqualTo(societyRows + 1);
	}
	
	@Test
	void aJoinerAddsTheirPincodeAndErrorsHideTheProposal() {
		Placement first = service.resolve(CITY, "200071", new LocationInput(null,
			new LocationInput.NewArea("Brook Field", "200071"), null, null, null, null, null, null), landlord);
		User second = data.user("Brook Joiner", Role.LANDLORD);
		Placement joined = service.resolve(CITY, "200072", new LocationInput(null,
			new LocationInput.NewArea(" brook  FIELD", "200072"), null, null, null, null, null, null), second);
		assertThat(joined.area().getPublicId()).isEqualTo(first.area().getPublicId());
		assertThat(areas.findByPublicId(first.area().getPublicId()).orElseThrow().pincodeList())
			.containsExactly("200071", "200072");
		
		User third = data.user("Brook Stranger", Role.LANDLORD);
		ApiException e = rejected(() -> service.resolve(CITY, "200074", new LocationInput(null,
			new LocationInput.NewArea("Brook Field", "200073"), null, null, null, null, null, null), third));
		assertThat(e.getCode()).isEqualTo("pincode_not_in_area");
		assertThat(e.getMessage()).doesNotContain("200071", "200072", "200073", "Brook");
		assertThat(e.getErrors().getFirst().message()).doesNotContain("200071", "200072", "200073");
	}
	
	@Test
	void aJoinerWithAHomeReadsAndReusesThePendingRowsById() throws Exception {
		LocationInput proposal = new LocationInput(null, new LocationInput.NewArea("Cedar Park", "200081"), null,
			new LocationInput.NewSociety("Cedar Heights", SocietyKind.GATED_COMMUNITY, null, null), null, null, null,
			null);
		Placement first = service.resolve(CITY, "200081", proposal, landlord);
		UUID areaId = first.area().getPublicId();
		UUID societyId = first.society().getPublicId();
		User joiner = data.user("Cedar Joiner", Role.LANDLORD);
		User stranger = data.user("Cedar Stranger", Role.LANDLORD);
		mvc.perform(get("/areas/{id}", areaId).header("Authorization", support.bearer(joiner)))
			.andExpect(status().isNotFound());
		
		Placement joined = service.resolve(CITY, "200081", proposal, joiner);
		locations.home(joiner, "Cedar Home", PropertyStatus.DRAFT, joined.area(), joined.society(), null, null);
		
		mvc.perform(get("/areas/{id}", areaId).header("Authorization", support.bearer(joiner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"));
		mvc.perform(get("/societies/{id}", societyId).header("Authorization", support.bearer(joiner)))
			.andExpect(status().isOk());
		mvc.perform(get("/properties").param("area", areaId.toString())
				.header("Authorization", support.bearer(joiner)))
			.andExpect(status().isOk());
		assertThat(service.resolve(CITY, "200081", LocationInput.of(areaId, societyId, null, null), joiner)
			.society().getPublicId()).isEqualTo(societyId);
		mvc.perform(get("/areas").param("city", CITY).header("Authorization", support.bearer(joiner)))
			.andExpect(jsonPath("$.items[*].name", not(hasItem("Cedar Park"))));
		
		mvc.perform(get("/areas/{id}", areaId).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/societies/{id}", societyId).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/properties").param("area", areaId.toString())
				.header("Authorization", support.bearer(stranger)))
			.andExpect(status().isUnprocessableContent());
		assertThat(rejected(() -> service.resolve(CITY, "200081", LocationInput.of(areaId, null, null, null),
			stranger)).getErrors().getFirst().code()).isEqualTo("not_found");
	}
	
	@Test
	void hiddenRowsTakeNoNewHomes() {
		Area hidden = locations.area(CITY, "Hidden Area", null, LocationStatus.HIDDEN, null, "200001");
		ApiException e = rejected(() -> service.resolve(CITY, "200001", place(hidden, null, null, null), landlord));
		assertThat(e.getErrors().getFirst().code()).isEqualTo("hidden");
		ApiException proposed = rejected(() -> service.resolve(CITY, "200001", new LocationInput(null,
			new LocationInput.NewArea("hidden area", "200001"), null, null, null, null, null, null), landlord));
		assertThat(proposed.getCode()).isEqualTo("location_exists");
	}
	
	@Test
	void validatesProposalFields() {
		ApiException e = rejected(() -> service.resolve(CITY, "200001", new LocationInput(area.getPublicId(), null,
			null, new LocationInput.NewSociety("x", null, "d".repeat(121), null), null, null, null, null), landlord));
		assertThat(e.getErrors()).extracting(f -> f.field()).containsExactly("location.newSociety.name",
			"location.newSociety.kind", "location.newSociety.developer");
	}
	
}
