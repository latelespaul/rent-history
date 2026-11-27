package com.rentrix.rentrixserver.review;

import com.jayway.jsonpath.JsonPath;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.AdminConsole;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.RecordingEmailSender;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-175 (landlord-adds-tenant S2, the captain's option B): an owner's review of a tenant, its count and its rating go
 * only to the owners D-54 allows and admins; never to the tenant it is about, another tenant or a signed-out visitor.
 * One test per viewer, on every response that can carry such a review.
 */
@IntegrationTest
@TestPropertySource(properties = "app.ratings.headline=all")
@Transactional
class TenantReviewVisibilityTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
	private static final String BODY = "Paid on time every month and kept the home clean.";
	private static final String TENANT_PART = """
		{"tenant": {"ratingOverall": 4, "criteria": {"RENT_ON_TIME": 5, "CARE_OF_HOME": 4, "COMMUNICATION": 4,
		            "HOUSE_RULES": 4, "MOVE_OUT": 3}, "wouldRecommend": true, "body": "%s"}}
		""".formatted(BODY);

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	ReviewRepository reviews;

	@Autowired
	TenancyRepository tenancies;

	@Autowired
	ReviewService service;

	@Autowired
	AccessTokenService tokens;

	@Autowired
	RecordingEmailSender mail;

	/** The cast: the owner who wrote the review, the tenant it is about, and the tenancy. */
	private record Cast(User author, User tenant, Tenancy tenancy, String reviewId) {
	}

	private Property home(User landlord) {
		return data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED);
	}

	/** A current stay a year old (D-39 window open) with the owner's review of the tenant, still waiting (D-46). */
	private Cast reviewed() throws Exception {
		User author = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = data.tenancy(home(author), tenant, TODAY.minusYears(1), null, TenancyStatus.ACTIVE);
		String json = mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/review-form")
				.header("Authorization", support.bearer(author))
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_PART))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tenant.status").value("AWAITING_REVEAL"))
			.andReturn().getResponse().getContentAsString();
		return new Cast(author, tenant, tenancy, JsonPath.read(json, "$.tenant.id"));
	}

	/** The 14 days run out with no counter-review, so the owner's review publishes alone (D-46). */
	private void reveal(Cast cast) {
		Review review = reviews.findByPublicId(UUID.fromString(cast.reviewId())).orElseThrow();
		review.setSubmittedAt(Instant.now().minus(Duration.ofDays(15)));
		reviews.saveAndFlush(review);
		assertThat(service.revealDue()).isEqualTo(1);
	}

	private ResultActions as(String bearer, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(bearer == null ? request : request.header("Authorization", bearer));
	}

	private String body(String bearer, String path) throws Exception {
		return as(bearer, get(path)).andReturn().getResponse().getContentAsString();
	}

	@Test
	void theTenantItIsAboutGetsNoReviewCountRatingOrHint() throws Exception {
		Cast cast = reviewed();
		String tenant = support.bearer(cast.tenant());
		String profile = "/users/" + cast.tenant().getPublicId();

		// While it waits: no "pending" entry, and the tenant's own task does not say the owner's review waits.
		as(tenant, get("/me/reviews/received")).andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.page.totalItems").value(0));
		as(tenant, get("/me/reviews/received").param("as", "TENANT")).andExpect(jsonPath("$.items", hasSize(0)));
		as(tenant, get("/me/review-tasks"))
			.andExpect(jsonPath("$[?(@.direction == 'TENANT_TO_LANDLORD')].otherSideWaiting").value(false));

		reveal(cast);

		// Their own profile keeps its label and badges, but no rating as tenant; the list is 404 as for a stranger.
		as(tenant, get(profile)).andExpect(status().isOk())
			.andExpect(jsonPath("$.label").value("Tenant"))
			.andExpect(jsonPath("$.tenantRating").doesNotExist());
		as(tenant, get(profile + "/reviews").param("as", "TENANT")).andExpect(status().isNotFound());
		as(tenant, get("/reviews/" + cast.reviewId())).andExpect(status().isNotFound());
		as(tenant, get("/me/reviews/received")).andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.page.totalItems").value(0));
		as(tenant, get("/me/reviews/received").param("as", "TENANT")).andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.page.totalItems").value(0));
		as(tenant, post("/reviews/" + cast.reviewId() + "/response").contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\": \"That is not what happened.\"}")).andExpect(status().isNotFound());
		for (String path : List.of(profile, "/me/reviews/received", "/me/review-tasks", "/me/tenancies",
			"/tenancies/" + cast.tenancy().getPublicId())) {
			assertThat(body(tenant, path)).as(path).doesNotContain(cast.reviewId(), BODY, "RENT_ON_TIME");
		}
		// The publish sends the tenant no "New review" email; the owner still gets one for the tenant's review.
		assertThat(mail.to(cast.tenant().getEmail())).noneMatch(m -> m.subject().startsWith("New review"));
		mvc.perform(post("/tenancies/" + cast.tenancy().getPublicId() + "/reviews").header("Authorization", tenant)
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"direction": "TENANT_TO_LANDLORD", "ratingOverall": 4, "ratingA": 4, "body": "%s"}
					""".formatted("Fixed every repair within a week and returned the deposit in full.")))
			.andExpect(status().isCreated());
		assertThat(mail.to(cast.author().getEmail())).anyMatch(m -> m.subject().startsWith("New review"));
		assertThat(mail.to(cast.tenant().getEmail())).noneMatch(m -> m.subject().startsWith("New review"));
	}

	@Test
	void anotherTenantAndSignedOutVisitorsGetNothing() throws Exception {
		Cast cast = reviewed();
		reveal(cast);
		String profile = "/users/" + cast.tenant().getPublicId();
		String other = support.bearer(data.approved("Omar Other", Role.TENANT));

		as(other, get(profile)).andExpect(status().isNotFound());
		as(other, get(profile + "/reviews").param("as", "TENANT")).andExpect(status().isNotFound());
		as(other, get("/reviews/" + cast.reviewId())).andExpect(status().isNotFound());
		as(other, get("/me/reviews/received")).andExpect(jsonPath("$.items", hasSize(0)));

		as(null, get(profile)).andExpect(status().isUnauthorized());
		as(null, get(profile + "/reviews").param("as", "TENANT")).andExpect(status().isUnauthorized());
		as(null, get("/reviews/" + cast.reviewId())).andExpect(status().isUnauthorized());
		// The public surfaces of the home never carry the review of its tenant.
		String propertyId = cast.tenancy().getProperty().getPublicId().toString();
		for (String path : List.of("/properties/" + propertyId, "/properties/" + propertyId + "/reviews",
			"/properties/" + propertyId + "/rating", "/properties?city=BLR")) {
			assertThat(body(null, path)).as(path).doesNotContain(cast.reviewId(), BODY, "RENT_ON_TIME");
		}
	}

	@Test
	void anotherOwnerOfTheTenantSeesTheRatingTopicsAndReviews() throws Exception {
		Cast cast = reviewed();
		reveal(cast);
		// The tenant's landlord on an earlier stay is an owner D-54 allows.
		User other = data.approved("Om Owner", Role.LANDLORD);
		Tenancy earlier = data.tenancy(home(other), cast.tenant(), TODAY.minusYears(4), TODAY.minusYears(3),
			TenancyStatus.ENDED);
		earlier.setEndedAt(TODAY.minusYears(3).atStartOfDay().toInstant(ZoneOffset.UTC));
		tenancies.save(earlier);
		String owner = support.bearer(other);
		String profile = "/users/" + cast.tenant().getPublicId();

		as(owner, get(profile)).andExpect(status().isOk())
			.andExpect(jsonPath("$.tenantRating.reviewCount").value(1))
			.andExpect(jsonPath("$.tenantRating.criteria", hasSize(5)))
			.andExpect(jsonPath("$.tenantRating.criteria[0].criterion").value("RENT_ON_TIME"))
			.andExpect(jsonPath("$.tenantRating.criteria[0].ratingCount").value(1))
			.andExpect(jsonPath("$.tenantRating.recommend.answered").value(1))
			.andExpect(jsonPath("$.tenantRating.recommend.yes").value(1))
			.andExpect(jsonPath("$.shortName").doesNotExist());
		as(owner, get(profile + "/reviews").param("as", "TENANT")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].body").value(BODY))
			.andExpect(jsonPath("$.items[0].author.label").value("Owner"))
			// D-169, D-171: no stay dates or exact times to anyone but the author and admins
			.andExpect(jsonPath("$.items[0].period").doesNotExist())
			.andExpect(jsonPath("$.items[0].publishedAt").doesNotExist());
		as(owner, get("/reviews/" + cast.reviewId())).andExpect(status().isOk());
		// The author sees it too; an owner with no relation to the tenant still gets 404 (D-54).
		as(support.bearer(cast.author()), get(profile + "/reviews").param("as", "TENANT"))
			.andExpect(jsonPath("$.items", hasSize(1)));
		String stranger = support.bearer(data.approved("Sam Stranger", Role.LANDLORD));
		as(stranger, get(profile)).andExpect(status().isNotFound());
		as(stranger, get("/reviews/" + cast.reviewId())).andExpect(status().isNotFound());
	}

	@Test
	void anAdminSeesEverything() throws Exception {
		Cast cast = reviewed();
		reveal(cast);
		String admin = AdminConsole.adminBearer(tokens, data.user("Ada Admin", Role.ADMIN));
		String profile = "/users/" + cast.tenant().getPublicId();

		mvc.perform(get(profile).with(AdminConsole.adminHost()).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tenantRating.reviewCount").value(1))
			.andExpect(jsonPath("$.tenantRating.criteria", hasSize(5)));
		mvc.perform(get(profile + "/reviews").param("as", "TENANT").with(AdminConsole.adminHost())
				.header("Authorization", admin))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].period").exists());
		mvc.perform(get("/reviews/" + cast.reviewId()).with(AdminConsole.adminHost()).header("Authorization", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body").value(BODY));
	}

	@Test
	void aTenantsRecordThatOnlyTheOwnersReviewBlocksIsRefusedWithoutNamingIt() throws Exception {
		User owner = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = data.tenancy(home(owner), tenant, TODAY.minusYears(1), null, TenancyStatus.ACTIVE);
		tenancy.setInitiatedBy(TenancyParty.TENANT); // the tenant's own record, never confirmed (D-142)
		tenancies.save(tenancy);
		mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/review-form")
				.header("Authorization", support.bearer(owner))
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_PART))
			.andExpect(status().isCreated());

		mvc.perform(delete("/tenancies/" + tenancy.getPublicId()).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("owner_has_records"));
		assertThat(tenancies.findById(tenancy.getId()).orElseThrow().getStatus()).isEqualTo(TenancyStatus.ACTIVE);
	}

}
