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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-164: the search box finds homes by their society or tower, typos allowed, and a flat directly by "society + flat
 * number" ({@code GET /properties?q=}), and suggests societies, areas and open flats as the user types
 * ({@code GET /search/suggestions}). A masked flat number is never matched or offered (D-57, D-140).
 */
@IntegrationTest
@Transactional
class PropertySocietySearchTest {

	private static final String CITY = "ZQV";

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	LocationTestSupport locations;

	@Autowired
	PropertyRepository properties;

	private User landlord;
	private Area area;
	private Society lakeside;
	private SocietyTower towerA;
	private SocietyTower towerB;
	private Property a1204;
	private Property b1204;
	private Property b1205;
	private Property masked;

	@BeforeEach
	void societies() {
		support.city(CITY, "Society Search City", true);
		landlord = data.user("Society Landlord", Role.LANDLORD);
		area = locations.area(CITY, "Whitefield Test", "560066");
		lakeside = locations.society(area, "Prestige Lakeside Habitat");
		towerA = locations.tower(lakeside, "Tower A", 1);
		towerB = locations.tower(lakeside, "Tower B", 2);
		Society other = locations.society(area, "Brigade Meadows");
		a1204 = flat(lakeside, towerA, "1204", FieldVisibility.OPEN);
		b1204 = flat(lakeside, towerB, "B-1204", FieldVisibility.OPEN);
		b1205 = flat(lakeside, towerB, "1205", FieldVisibility.OPEN);
		masked = flat(lakeside, towerB, "1301", FieldVisibility.MASKED);
		flat(other, null, "1204", FieldVisibility.OPEN);
		flat(null, null, "7", FieldVisibility.OPEN);
	}

	private Property flat(Society society, SocietyTower tower, String unit, FieldVisibility visibility) {
		Property home = locations.home(landlord, null, PropertyStatus.PUBLISHED, area, society, tower, null);
		home.setUnitLabel(unit);
		home.setUnitLabelVisibility(visibility);
		return properties.saveAndFlush(home);
	}

	private List<String> ids(Property... homes) {
		return java.util.Arrays.stream(homes).map(p -> p.getPublicId().toString()).toList();
	}

	@Test
	void findsTheHomesOfASocietyByItsNameWithTyposAndOfATower() throws Exception {
		for (String q : List.of("prestige lakeside", "Prestge Lakesde", "lakeside habitat")) {
			mvc.perform(get("/properties").param("city", CITY).param("q", q))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalItems").value(4))
				.andExpect(jsonPath("$.items[*].id",
					containsInAnyOrder(ids(a1204, b1204, b1205, masked).toArray())));
		}
		mvc.perform(get("/properties").param("city", CITY).param("q", "lakeside tower b"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1204, b1205, masked).toArray())));
		mvc.perform(get("/properties").param("city", CITY).param("q", "Lakeside B"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1204, b1205, masked).toArray())));
		// too many typos find nothing
		mvc.perform(get("/properties").param("city", CITY).param("q", "pxxxtige"))
			.andExpect(jsonPath("$.page.totalItems").value(0));
	}

	@Test
	void findsAnOpenFlatDirectlyBySocietyAndFlatNumber() throws Exception {
		mvc.perform(get("/properties").param("city", CITY).param("q", "Prestige Lakeside 1205"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1205).toArray())));
		// "B-1204" is flat 1204 of Tower B, or a flat labelled B-1204
		for (String q : List.of("Lakeside B-1204", "lakeside b1204", "Lakeside tower B 1204")) {
			mvc.perform(get("/properties").param("city", CITY).param("q", q))
				.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1204).toArray())));
		}
		// 1204 alone in the society: Tower A's 1204 and Tower B's B-1204, never Brigade Meadows' 1204
		mvc.perform(get("/properties").param("city", CITY).param("q", "lakeside flat 1204"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(a1204, b1204).toArray())));
		// inside ?society= the text is the flat number
		mvc.perform(get("/properties").param("society", lakeside.getPublicId().toString()).param("q", "1205"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1205).toArray())));
	}

	@Test
	void aSocietyNameEndingInANumberFindsOnlyThatSociety() throws Exception {
		Property numbered = flat(locations.society(area, "Prestige Lakeside 2"), null, "101", FieldVisibility.OPEN);
		mvc.perform(get("/properties").param("city", CITY).param("q", "Prestige Lakeside 2"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(numbered).toArray())));
		mvc.perform(get("/properties").param("city", CITY).param("q", "Prestige Lakeside 1205"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1205).toArray())));
	}

	@Test
	void aSocietyNameEndingInANumberBeatsAFlatWithThatNumberInANearName() throws Exception {
		Property numbered = flat(locations.society(area, "Prestige Lakeside 2"), null, "101", FieldVisibility.OPEN);
		flat(lakeside, towerA, "2", FieldVisibility.OPEN);
		mvc.perform(get("/properties").param("city", CITY).param("q", "Prestige Lakeside 2"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(numbered).toArray())));
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "Prestige Lakeside 2"))
			.andExpect(jsonPath("$.societies[0].name").value("Prestige Lakeside 2"))
			.andExpect(jsonPath("$.homes", empty()));
	}

	@Test
	void villaBeforeTheFlatNumberIsAFillerWord() throws Exception {
		mvc.perform(get("/properties").param("city", CITY).param("q", "Lakeside villa 1205"))
			.andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ids(b1205).toArray())));
	}
	
	@Test
	void aMaskedFlatNumberIsNeverMatchedSoItCannotBeProbed() throws Exception {
		// no open flat has 1301 (only a masked one does) and none has 9999: both give the society's homes alike
		for (String q : List.of("Prestige Lakeside 1301", "Prestige Lakeside 9999")) {
			mvc.perform(get("/properties").param("city", CITY).param("q", q))
				.andExpect(jsonPath("$.page.totalItems").value(4))
				.andExpect(jsonPath("$.items[*].unitLabel").doesNotExist());
		}
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "Prestige Lakeside 13"))
			.andExpect(jsonPath("$.homes", empty()));
		mvc.perform(get("/properties").param("society", lakeside.getPublicId().toString()).param("q", "1301"))
			.andExpect(jsonPath("$.page.totalItems").value(0));
	}

	@Test
	void plainTextSearchStillFindsLocalitiesAndStandaloneHomes() throws Exception {
		mvc.perform(get("/properties").param("city", CITY).param("q", "whitefield"))
			.andExpect(jsonPath("$.page.totalItems").value(6));
		mvc.perform(get("/properties").param("city", CITY).param("q", "brigade 7"))
			.andExpect(jsonPath("$.page.totalItems").value(1));
		mvc.perform(get("/properties").param("city", CITY).param("q", "560066"))
			.andExpect(jsonPath("$.items[*].id", hasSize(6)));
		mvc.perform(get("/properties").param("city", CITY).param("q", "whitefield 7"))
			.andExpect(jsonPath("$.page.totalItems").value(0));
	}

	@Test
	void suggestsSocietiesWithTheirTowerAreasAndOpenFlatsAnonymously() throws Exception {
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "prestge lak"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.societies", hasSize(1)))
			.andExpect(jsonPath("$.societies[0].id").value(lakeside.getPublicId().toString()))
			.andExpect(jsonPath("$.societies[0].name").value("Prestige Lakeside Habitat"))
			.andExpect(jsonPath("$.societies[0].area.name").value("Whitefield Test"))
			.andExpect(jsonPath("$.societies[0].city.code").value(CITY))
			.andExpect(jsonPath("$.societies[0].publishedHomes").value(4))
			.andExpect(jsonPath("$.societies[0].tower").doesNotExist())
			.andExpect(jsonPath("$.homes", empty()));
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "lakeside tower b"))
			.andExpect(jsonPath("$.societies[0].tower.id").value(towerB.getPublicId().toString()))
			.andExpect(jsonPath("$.societies[0].tower.name").value("Tower B"))
			.andExpect(jsonPath("$.societies[0].publishedHomes").value(3));
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "Lakeside 12"))
			.andExpect(jsonPath("$.homes[*].id", containsInAnyOrder(ids(a1204, b1204, b1205).toArray())))
			.andExpect(jsonPath("$.homes[0].society.name").value("Prestige Lakeside Habitat"))
			.andExpect(jsonPath("$.homes[0].unitLabel").value("1204"))
			.andExpect(jsonPath("$.homes[0].tower.name").value("Tower A"));
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "whitefeld"))
			.andExpect(jsonPath("$.areas[0].name").value("Whitefield Test"))
			.andExpect(jsonPath("$.areas[0].city.code").value(CITY));
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "p"))
			.andExpect(jsonPath("$.societies", empty()))
			.andExpect(jsonPath("$.areas", empty()));
	}

	@Test
	void suggestionsHideOthersEmptyProposalsAndCheckTheCity() throws Exception {
		User proposer = data.user("Proposing Landlord", Role.LANDLORD);
		locations.society(area, "Prestige Proposed Court", null, SocietyKind.APARTMENT_COMPLEX, LocationStatus.PENDING,
			proposer);
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "prestige"))
			.andExpect(jsonPath("$.societies[*].name", containsInAnyOrder("Prestige Lakeside Habitat")));
		mvc.perform(get("/search/suggestions").param("city", CITY).param("q", "prestige")
				.header("Authorization", support.bearer(proposer)))
			.andExpect(jsonPath("$.societies[*].name",
				containsInAnyOrder("Prestige Lakeside Habitat", "Prestige Proposed Court")));
		support.city("ZQW", "Dormant Suggest City", false);
		mvc.perform(get("/search/suggestions").param("city", "ZQW").param("q", "prestige"))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(get("/search/suggestions").param("q", "x".repeat(101)))
			.andExpect(status().isUnprocessableContent());
	}

}
