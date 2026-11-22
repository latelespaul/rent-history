package com.rentrix.rentrixserver.stats;

import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /stats}: real counts per live city, zeros included (D-03). */
@IntegrationTest
@Transactional
class StatsApiTest {
	
	private static final String CITY = "ZQT";
	private static final String ROW = "$.cities[?(@.city == '" + CITY + "')]";
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	PropertyTestSupport support;
	
	@Autowired
	ReviewRepository reviews;
	
	@Autowired
	EntityManager entityManager;
	
	@Test
	void everyLiveCityHasARowWithZeros() throws Exception {
		support.city(CITY, "Stats Test City", true);
		support.city("ZQU", "Dormant Stats City", false);
		mvc.perform(get("/stats"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cities[?(@.city == 'BLR')]").exists())
			.andExpect(jsonPath("$.cities[?(@.city == 'PNQ')]").exists())
			.andExpect(jsonPath("$.cities[?(@.city == 'ZQU')]").doesNotExist())
			.andExpect(jsonPath(ROW + ".publishedProperties").value(0))
			.andExpect(jsonPath(ROW + ".confirmedTenancies").value(0))
			.andExpect(jsonPath(ROW + ".publishedReviews").value(0));
	}
	
	@Test
	void countsPublishedPropertiesConfirmedTenanciesAndPublishedReviews() throws Exception {
		support.city(CITY, "Stats Test City", true);
		User landlord = data.user("Stats Landlord", Role.LANDLORD);
		User tenant = data.user("Stats Tenant", Role.TENANT);
		Property published = data.property(landlord, CITY, "Area", PropertyStatus.PUBLISHED);
		data.property(landlord, CITY, "Area", PropertyStatus.DRAFT);
		Property archived = data.property(landlord, CITY, "Area", PropertyStatus.ARCHIVED);
		LocalDate start = LocalDate.of(2024, 1, 1);
		Tenancy ended = data.tenancy(archived, tenant, start, start.plusYears(1), TenancyStatus.ENDED);
		data.tenancy(published, tenant, start.plusYears(2), null, TenancyStatus.ACTIVE);
		data.tenancy(published, data.user("Pending Tenant", Role.TENANT), start, null,
			TenancyStatus.PENDING_CONFIRMATION);
		review(ended, ReviewDirection.TENANT_TO_PROPERTY, ReviewStatus.PUBLISHED);
		review(ended, ReviewDirection.TENANT_TO_LANDLORD, ReviewStatus.AWAITING_REVEAL);
		entityManager.flush();
		mvc.perform(get("/stats"))
			.andExpect(jsonPath(ROW + ".publishedProperties").value(1))
			.andExpect(jsonPath(ROW + ".confirmedTenancies").value(2))
			.andExpect(jsonPath(ROW + ".publishedReviews").value(1));
	}
	
	private void review(Tenancy tenancy, ReviewDirection direction, ReviewStatus status) {
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(direction);
		review.setAuthor(tenancy.getTenant());
		review.setSubjectUser(direction == ReviewDirection.TENANT_TO_LANDLORD ? tenancy.getLandlord() : null);
		review.setProperty(tenancy.getProperty());
		review.setRatingOverall(4);
		review.setBody("A calm, well kept home with a responsive owner overall.");
		review.setStatus(status);
		review.setSubmittedAt(Instant.parse("2025-02-01T00:00:00Z"));
		reviews.save(review);
	}
	
}
