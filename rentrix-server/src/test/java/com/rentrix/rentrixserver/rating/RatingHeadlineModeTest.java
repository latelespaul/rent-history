package com.rentrix.rentrixserver.rating;

import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.security.AccessTokenService;
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

import static com.rentrix.rentrixserver.support.AdminConsole.adminBearer;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The headline mode {@code app.ratings.headline} (D-135): {@code verified} by default (D-163), {@code all} on request. */
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
	
	@Autowired
	AccessTokenService tokens;
	
	@Test
	void defaultModeIsVerified() {
		assertThat(properties.headline()).isEqualTo(RatingProperties.Headline.VERIFIED);
		assertThat(new RatingProperties(null).headline()).isEqualTo(RatingProperties.Headline.VERIFIED);
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
		// The default (D-163): the review is listed, but the headline counts none until the tick.
		mvc.perform(get("/properties/" + home.getPublicId() + "/rating"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewCount").value(0));
		mvc.perform(get("/properties/" + home.getPublicId() + "/reviews"))
			.andExpect(jsonPath("$.items[0].verification").value("UNVERIFIED"));
		RatingRecomputer verifiedOnly = recomputer;
		RatingRecomputer all = new RatingRecomputer(source, aggregates, new RatingProperties(RatingProperties.Headline.ALL));
		
		assertThat(all.recompute(RatingSubjectType.PROPERTY, home.getId()).getReviewCount())
			.as("all: the unverified published review counts").isEqualTo(1);
		assertThat(verifiedOnly.recompute(RatingSubjectType.PROPERTY, home.getId()).getReviewCount())
			.as("verified: not without the tick").isZero();
		
		jdbc.update("update reviews set verification_status = 'VERIFIED' where tenancy_id = ?", tenancy.getId());
		assertThat(verifiedOnly.recompute(RatingSubjectType.PROPERTY, home.getId()).getRatingSum()).isEqualTo(4);
		
		jdbc.update("update reviews set verification_status = 'REJECTED' where tenancy_id = ?", tenancy.getId());
		assertThat(verifiedOnly.recompute(RatingSubjectType.PROPERTY, home.getId()).getReviewCount())
			.as("verified: rejected reviews stay visible but never count").isZero();
		assertThat(all.recompute(RatingSubjectType.PROPERTY, home.getId()).getReviewCount())
			.as("all: still counted").isEqualTo(1);
	}
	
	@Test
	void theAdminRepairRebuildsRowsStoredUnderTheOldModeAtOnce() throws Exception {
		User landlord = aged(data.approved("Ravi Landlord", Role.LANDLORD));
		User tenant = aged(data.approved("Rina Tenant", Role.TENANT));
		Property home = data.property(landlord, "BLR", "Indiranagar", PropertyStatus.PUBLISHED);
		Tenancy tenancy = data.tenancy(home, tenant, LocalDate.now(ZoneOffset.UTC).minusYears(1), null,
			TenancyStatus.ACTIVE);
		mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/reviews")
				.header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"direction": "TENANT_TO_PROPERTY", "ratingOverall": 4, "ratingA": 4,
					 "body": "Bright flat, the owner fixed the geyser within a day and returned calls quickly."}
					"""))
			.andExpect(status().isCreated());
		// The row as an `all` deployment stored it before the switch to `verified`.
		new RatingRecomputer(source, aggregates, new RatingProperties(RatingProperties.Headline.ALL))
			.recompute(RatingSubjectType.PROPERTY, home.getId());
		mvc.perform(get("/properties/" + home.getPublicId() + "/rating"))
			.andExpect(jsonPath("$.reviewCount").value(1));
		
		User admin = data.user("Repair Admin", Role.ADMIN);
		mvc.perform(post("/admin/ratings/recompute").with(adminHost())
				.header("Authorization", adminBearer(tokens, tenant)))
			.andExpect(status().isForbidden());
		mvc.perform(post("/admin/ratings/recompute").header("Authorization", adminBearer(tokens, admin)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/properties/" + home.getPublicId() + "/rating"))
			.andExpect(jsonPath("$.reviewCount").value(1));
		
		mvc.perform(post("/admin/ratings/recompute").with(adminHost())
				.header("Authorization", adminBearer(tokens, admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rebuilt").isNumber());
		mvc.perform(get("/properties/" + home.getPublicId() + "/rating"))
			.andExpect(jsonPath("$.reviewCount").value(0));
	}
	
	private User aged(User user) {
		Instant created = Instant.now().minus(Duration.ofDays(30));
		user.setCreatedAt(created);
		jdbc.update("update users set created_at = ? where id = ?", Timestamp.from(created), user.getId());
		return user;
	}
	
}
