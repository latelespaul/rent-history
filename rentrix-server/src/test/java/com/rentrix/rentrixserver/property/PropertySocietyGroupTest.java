package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationTestSupport;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.RentRecord;
import com.rentrix.rentrixserver.tenancy.RentRecordRepository;
import com.rentrix.rentrixserver.tenancy.RentRecordStatus;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-166: {@code GET /properties/grouped} shows a society with 2+ matching homes as one row with its flat count,
 * listed and verified reviews, verified average and (signed in only) rent range; a society with one matching home
 * and a standalone home stay home rows. {@code GET /societies/{id}/summary} gives the same figures over all of a
 * society's published homes.
 */
@IntegrationTest
@Transactional
class PropertySocietyGroupTest {

	private static final String CITY = "ZRG";

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

	@Autowired
	RentRecordRepository rentRecords;

	private User landlord;
	private Area area;
	private Society lakeside;
	private Property a1;
	private Property a2;
	private Property b1;
	private Property solo;
	private Property standalone;

	@BeforeEach
	void homes() {
		support.city(CITY, "Group Test City", true);
		landlord = data.user("Group Landlord", Role.LANDLORD);
		area = locations.area(CITY, "Group Area", "560002");
		lakeside = locations.society(area, "Group Lakeside");
		SocietyTower towerA = locations.tower(lakeside, "Tower A", 1);
		SocietyTower towerB = locations.tower(lakeside, "Tower B", 2);
		Society meadows = locations.society(area, "Group Meadows");
		a1 = locations.home(landlord, null, PropertyStatus.PUBLISHED, area, lakeside, towerA, null);
		a2 = locations.home(landlord, null, PropertyStatus.PUBLISHED, area, lakeside, towerA, null);
		b1 = locations.home(landlord, null, PropertyStatus.PUBLISHED, area, lakeside, towerB, null);
		solo = locations.home(landlord, null, PropertyStatus.PUBLISHED, area, meadows, null, null);
		standalone = locations.home(landlord, "Solo House", PropertyStatus.PUBLISHED, area, null, null, null);
		locations.home(landlord, null, PropertyStatus.DRAFT, area, lakeside, towerB, null);
		// verified reviews: 2 × 4.0 and 2 × 5.0 pool to 4 counted reviews averaging 4.5
		support.rating(a1, 2, 4.0);
		support.rating(b1, 2, 5.0);
		rent(a1, 30000, FieldVisibility.OPEN);
		rent(b1, 45000, FieldVisibility.OPEN);
		rent(a2, 99000, FieldVisibility.MASKED);
	}

	private void rent(Property home, long amount, FieldVisibility visibility) {
		Tenancy tenancy = data.tenancy(home, data.user("Group Tenant " + amount, Role.TENANT),
			LocalDate.of(2025, 1, 1), null, TenancyStatus.ACTIVE);
		tenancy.setRentVisibility(visibility);
		RentRecord record = new RentRecord();
		record.setTenancy(tenancy);
		record.setEffectiveFrom(tenancy.getStartDate());
		record.setMonthlyRentInr(amount);
		record.setAddedBy(landlord);
		record.setStatus(RentRecordStatus.CONFIRMED);
		rentRecords.saveAndFlush(record);
	}

	@Test
	void aSocietyWithTwoOrMoreMatchingHomesIsOneRow() throws Exception {
		mvc.perform(get("/properties/grouped").param("city", CITY).param("sort", "top_rated"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalHomes").value(5))
			.andExpect(jsonPath("$.page.totalItems").value(3))
			.andExpect(jsonPath("$.items[*].kind", contains("SOCIETY", "HOME", "HOME")))
			.andExpect(jsonPath("$.items[0].society.id").value(lakeside.getPublicId().toString()))
			.andExpect(jsonPath("$.items[0].society.name").value("Group Lakeside"))
			.andExpect(jsonPath("$.items[0].society.area.name").value("Group Area"))
			.andExpect(jsonPath("$.items[0].society.city.code").value(CITY))
			.andExpect(jsonPath("$.items[0].society.flats").value(3))
			.andExpect(jsonPath("$.items[0].society.rating.reviewCount").value(4))
			.andExpect(jsonPath("$.items[0].society.rating.average").value(4.5))
			// rent history needs sign-in (D-56): no range for a signed-out visitor
			.andExpect(jsonPath("$.items[0].society.rentRange").doesNotExist())
			.andExpect(jsonPath("$.items[0].home").doesNotExist());
		mvc.perform(get("/properties/grouped").param("city", CITY).param("sort", "newest"))
			.andExpect(jsonPath("$.items[*].kind", contains("HOME", "HOME", "SOCIETY")))
			.andExpect(jsonPath("$.items[0].home.id").value(standalone.getPublicId().toString()))
			.andExpect(jsonPath("$.items[1].home.id").value(solo.getPublicId().toString()));
	}

	@Test
	void signedInCallersGetTheRangeOfOpenRentsOnly() throws Exception {
		mvc.perform(get("/properties/grouped").param("city", CITY).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.items[0].society.rentRange.min").value(30000))
			.andExpect(jsonPath("$.items[0].society.rentRange.max").value(45000));
	}

	@Test
	void filtersApplyBeforeGroupingSoOneMatchingFlatIsAHomeRow() throws Exception {
		a1.setHomeSize(HomeSize.BHK3);
		properties.saveAndFlush(a1);
		mvc.perform(get("/properties/grouped").param("city", CITY).param("homeSize", "BHK3"))
			.andExpect(jsonPath("$.totalHomes").value(1))
			.andExpect(jsonPath("$.items[*].kind", contains("HOME")))
			.andExpect(jsonPath("$.items[0].home.id").value(a1.getPublicId().toString()));
		mvc.perform(get("/properties/grouped").param("city", CITY).param("q", "group lakeside tower a"))
			.andExpect(jsonPath("$.items[*].kind", contains("SOCIETY")))
			.andExpect(jsonPath("$.items[0].society.flats").value(2));
	}

	@Test
	void theSocietySummaryCoversAllItsPublishedHomes() throws Exception {
		mvc.perform(get("/societies/{id}/summary", lakeside.getPublicId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flats").value(3))
			.andExpect(jsonPath("$.rating.average").value(4.5))
			.andExpect(jsonPath("$.listedReviews").value(0))
			.andExpect(jsonPath("$.rentRange").doesNotExist());
		mvc.perform(get("/societies/{id}/summary", lakeside.getPublicId())
				.header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.rentRange.min").value(30000));
		mvc.perform(get("/societies/{id}/summary", UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	
	/**
	 * A society a tenant typed is PENDING (D-109), yet its published homes are in search (D-162); its page, summary,
	 * pending tower and filters open for anyone, so the society card never leads to "not found" (D-166).
	 */
	@Test
	void aTypedSocietyWithPublishedHomesOpensForAnyone() throws Exception {
		User tenant = data.user("Typing Tenant", Role.TENANT);
		Society typed = locations.society(area, "Typed Heights", null,
			com.rentrix.rentrixserver.location.SocietyKind.APARTMENT_COMPLEX,
			com.rentrix.rentrixserver.location.LocationStatus.PENDING, tenant);
		SocietyTower typedTower = locations.tower(typed, "Tower T", 1,
			com.rentrix.rentrixserver.location.LocationStatus.PENDING, tenant);
		locations.home(landlord, null, PropertyStatus.PUBLISHED, area, typed, typedTower, null);
		locations.home(landlord, null, PropertyStatus.PUBLISHED, area, typed, typedTower, null);
		mvc.perform(get("/societies/{id}", typed.getPublicId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.towers[*].name", contains("Tower T")));
		mvc.perform(get("/societies/{id}/summary", typed.getPublicId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flats").value(2))
			.andExpect(jsonPath("$.pending").value(true));
		mvc.perform(get("/properties").param("society", typed.getPublicId().toString())
				.param("tower", typedTower.getPublicId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalItems").value(2));
	}
	
}
