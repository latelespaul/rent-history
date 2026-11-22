package com.rentrix.rentrixserver.rating;

import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The headline mode {@code app.ratings.headline} (D-135): {@code all} by default, {@code verified} for the tick. */
@IntegrationTest
@Transactional
class RatingHeadlineModeTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	PropertyTestSupport support;
	
	@Autowired
	JdbcTemplate jdbc;
	
	@Autowired
	RatingSourceRepository source;
	
	@Autowired
	RatingAggregateRepository aggregates;
	
	@Autowired
	RatingRecomputer recomputer;
	
	@Autowired
	RatingProperties properties;
	
	@Test
	void defaultModeIsAll() {
		assertThat(properties.headline()).isEqualTo(RatingProperties.Headline.ALL);
		assertThat(new RatingProperties(null).headline()).isEqualTo(RatingProperties.Headline.ALL);
	}
	
	@Test
	void allCountsEveryPublishedReviewWhileVerifiedCountsOnlyTheGreenTick() throws Exception {
		User landlord = aged(data.approved("Lata Landlord", Role.LANDLORD));
		User tenant = aged(data.approved("Tara Tenant", Role.TENANT));
		Property home = data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED);
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		Tenancy tenancy = data.tenancy(home, tenant, today.minusYears(1), null, TenancyStatus.ACTIVE);
		mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/reviews")
				.header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"direction": "TENANT_TO_PROPERTY", "ratingOverall": 4, "ratingA": 4,
					 "body": "Clean home, fair landlord and a quick reply to every repair request we made."}
					"""))
			.andExpect(status().isCreated());
		RatingRecomputer verifiedOnly = new RatingRecomputer(source, aggregates,
			new RatingProperties(RatingProperties.Headline.VERIFIED));
		
		assertThat(recomputer.recompute(RatingSubjectType.PROPERTY, home.getId()).getReviewCount())
			.as("all: the unverified published review counts").isEqualTo(1);
		assertThat(verifiedOnly.recompute(RatingSubjectType.PROPERTY, home.getId()).getReviewCount())
			.as("verified: not without the tick").isZero();
		
		jdbc.update("update reviews set verification_status = 'VERIFIED' where tenancy_id = ?", tenancy.getId());
		assertThat(verifiedOnly.recompute(RatingSubjectType.PROPERTY, home.getId()).getRatingSum()).isEqualTo(4);
		
		jdbc.update("update reviews set verification_status = 'REJECTED' where tenancy_id = ?", tenancy.getId());
		assertThat(verifiedOnly.recompute(RatingSubjectType.PROPERTY, home.getId()).getReviewCount())
			.as("verified: rejected reviews stay visible but never count").isZero();
		assertThat(recomputer.recompute(RatingSubjectType.PROPERTY, home.getId()).getReviewCount())
			.as("all: still counted").isEqualTo(1);
	}
	
	private User aged(User user) {
		Instant created = Instant.now().minus(Duration.ofDays(30));
		user.setCreatedAt(created);
		jdbc.update("update users set created_at = ? where id = ?", Timestamp.from(created), user.getId());
		return user;
	}
	
}
