package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationTestSupport;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-165: a search card carries what its title line needs, "Society · Tower B · Flat 1204", the area and city, and
 * every listed review next to the verified headline count. The flat number is on the card only when it is
 * {@code OPEN} (D-140); a {@code MASKED} one never is, not even for its owner (D-57).
 */
@IntegrationTest
@Transactional
class PropertyCardTitleTest {

	private static final String CITY = "ZQY";

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
	ReviewRepository reviews;

	@Autowired
	EntityManager entityManager;

	private User landlord;
	private Area area;
	private Society society;
	private SocietyTower tower;

	@BeforeEach
	void place() {
		support.city(CITY, "Card Title City", true);
		landlord = data.user("Card Landlord", Role.LANDLORD);
		area = locations.area(CITY, "Card Area", "560001");
		society = locations.society(area, "Prestige Card Lakeside");
		tower = locations.tower(society, "Tower B", 1);
	}

	private Property home(String unit, FieldVisibility visibility) {
		Property home = locations.home(landlord, null, PropertyStatus.PUBLISHED, area, society, tower, null);
		home.setUnitLabel(unit);
		home.setUnitLabelVisibility(visibility);
		return properties.saveAndFlush(home);
	}

	private void review(Property home, ReviewStatus status) {
		User tenant = data.user("Card Tenant " + status, Role.TENANT);
		Tenancy tenancy = data.tenancy(home, tenant, LocalDate.of(2024, 1, 1), null, TenancyStatus.ACTIVE);
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(ReviewDirection.TENANT_TO_PROPERTY);
		review.setAuthor(tenant);
		review.setProperty(home);
		review.setRatingOverall(4);
		review.setBody("A calm, well kept home with a responsive owner overall.");
		review.setStatus(status);
		review.setSubmittedAt(Instant.parse("2025-02-01T00:00:00Z"));
		reviews.save(review);
	}

	@Test
	void anOpenFlatNumberAndThePlaceAreOnTheCard() throws Exception {
		Property open = home("1204", FieldVisibility.OPEN);
		review(open, ReviewStatus.PUBLISHED);
		review(open, ReviewStatus.PUBLISHED);
		review(open, ReviewStatus.WITHDRAWN);
		entityManager.flush();
		mvc.perform(get("/properties").param("city", CITY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(open.getPublicId().toString()))
			.andExpect(jsonPath("$.items[0].unitLabel").value("1204"))
			.andExpect(jsonPath("$.items[0].location.society.name").value("Prestige Card Lakeside"))
			.andExpect(jsonPath("$.items[0].location.tower.name").value("Tower B"))
			.andExpect(jsonPath("$.items[0].location.area.name").value("Card Area"))
			.andExpect(jsonPath("$.items[0].city.name").value("Card Title City"))
			// both published reviews are listed; neither is verified, so the headline counts none (D-163)
			.andExpect(jsonPath("$.items[0].listedReviews").value(2))
			.andExpect(jsonPath("$.items[0].rating.reviewCount").value(0));
	}

	@Test
	void aMaskedFlatNumberIsNeverOnACard() throws Exception {
		home("1301", FieldVisibility.MASKED);
		for (String bearer : new String[] {null, support.bearer(landlord)}) {
			var request = get("/properties").param("city", CITY);
			if (bearer != null) {
				request.header("Authorization", bearer);
			}
			mvc.perform(request)
				.andExpect(jsonPath("$.items[0].unitLabel").doesNotExist())
				.andExpect(jsonPath("$.items[0].location.tower.name").value("Tower B"))
				.andExpect(jsonPath("$.items[0].listedReviews").value(0));
		}
	}

}
