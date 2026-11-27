package com.rentrix.rentrixserver.review;

import com.jayway.jsonpath.JsonPath;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.rating.RatingRecomputer;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The review form (D-157) and its per-parameter averages (D-158). */
@IntegrationTest
// The rating math here runs over reviews without the green tick, so it counts every published review
// ("all"); the default "verified" headline (D-163) is covered by RatingHeadlineModeTest.
@TestPropertySource(properties = "app.ratings.headline=all")
@Transactional
class ReviewFormApiTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

	/** Every home and landlord parameter at {@code rating}. */
	private static final String HOME_ALL = """
		{"WATER_SUPPLY": %1$d, "POWER_BACKUP": %1$d, "MAINTENANCE": %1$d, "CLEANLINESS": %1$d, "SECURITY": %1$d,
		 "PARKING": %1$d, "NOISE": %1$d, "NEIGHBOURHOOD": %1$d, "VALUE_FOR_RENT": %1$d}""";
	private static final String LANDLORD_ALL = """
		{"RESPONSIVENESS": %1$d, "DEPOSIT_RETURN": %1$d, "TRANSPARENCY": %1$d, "PRIVACY": %1$d, "BEHAVIOUR": %1$d}""";

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	ReviewRepository reviews;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RatingRecomputer recomputer;

	private Property home(User landlord) {
		return data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED);
	}

	/** A current tenancy that started a year ago, so the 60-day lock has passed (D-39). */
	private Tenancy active(Property property, User tenant) {
		return data.tenancy(property, tenant, TODAY.minusYears(1), null, TenancyStatus.ACTIVE);
	}

	private ResultActions form(User author, Tenancy tenancy, String json) throws Exception {
		return mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/review-form")
			.header("Authorization", support.bearer(author))
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String full(int home, int landlord) {
		return """
			{"home": {"ratingOverall": %d, "criteria": %s, "wouldRecommend": true, "pros": "Steady water all summer.",
			          "cons": "The road outside is noisy at night."},
			 "landlord": {"ratingOverall": %d, "criteria": %s, "wouldRecommend": true, "body": ""}}
			""".formatted(home, HOME_ALL.formatted(home), landlord, LANDLORD_ALL.formatted(landlord));
	}

	private static String homeOnly(int rating) {
		return """
			{"home": {"ratingOverall": %d, "criteria": %s, "wouldRecommend": %s}}
			""".formatted(rating, HOME_ALL.formatted(rating), rating >= 4);
	}

	@Test
	void formWritesTheHomeAndLandlordReviewsWithEveryParameter() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);

		String json = form(tenant, tenancy, full(4, 5))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.home.direction").value("TENANT_TO_PROPERTY"))
			.andExpect(jsonPath("$.home.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.home.ratingOverall").value(4))
			.andExpect(jsonPath("$.home.criteria.WATER_SUPPLY").value(4))
			.andExpect(jsonPath("$.home.criteria.VALUE_FOR_RENT").value(4))
			.andExpect(jsonPath("$.home.wouldRecommend").value(true))
			.andExpect(jsonPath("$.home.pros").value("Steady water all summer."))
			.andExpect(jsonPath("$.home.cons").value("The road outside is noisy at night."))
			.andExpect(jsonPath("$.home.body").doesNotExist())
			// the landlord review waits for the landlord's (D-46)
			.andExpect(jsonPath("$.landlord.direction").value("TENANT_TO_LANDLORD"))
			.andExpect(jsonPath("$.landlord.status").value("AWAITING_REVEAL"))
			.andExpect(jsonPath("$.landlord.criteria.RESPONSIVENESS").value(5))
			.andExpect(jsonPath("$.landlord.criteria.BEHAVIOUR").value(5))
			.andReturn().getResponse().getContentAsString();

		Review home = reviews.findByPublicId(UUID.fromString(JsonPath.read(json, "$.home.id"))).orElseThrow();
		assertThat(home.getCriteria()).hasSize(9).containsEntry(ReviewCriterion.NOISE, 4);
		assertThat(home.getBody()).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from review_criterion_ratings", Integer.class)).isEqualTo(14);

		// a second form for the same tenancy is refused (D-38)
		form(tenant, tenancy, homeOnly(3)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("review_exists"));
	}

	@Test
	void everyBrokenFieldComesBackInOneAnswerAndNothingIsSaved() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);

		form(tenant, tenancy, """
			{"home": {"ratingOverall": 4, "criteria": {"PARKING": 6, "WIFI": 3, "RESPONSIVENESS": 4, "NOISE": 2},
			          "pros": "Call me on 98450 12345 for details"},
			 "landlord": {"criteria": {"PRIVACY": 0}}}
			""")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("home.criteria.PARKING",
				"home.criteria.WIFI", "home.criteria.RESPONSIVENESS", "home.pros", "landlord.ratingOverall",
				"landlord.criteria.PRIVACY")))
			.andExpect(jsonPath("$.errors[?(@.field == 'home.criteria.PARKING')].code").value("out_of_range"))
			.andExpect(jsonPath("$.errors[?(@.field == 'home.criteria.WIFI')].code").value("unknown_criterion"))
			.andExpect(jsonPath("$.errors[?(@.field == 'home.criteria.RESPONSIVENESS')].code")
				.value("wrong_direction"))
			.andExpect(jsonPath("$.errors[?(@.field == 'home.pros')].code").value("contains_phone"))
			.andExpect(jsonPath("$.errors[?(@.field == 'landlord.ratingOverall')].code").value("not_null"));
		assertThat(reviews.findByTenancyId(tenancy.getId())).as("both or neither").isEmpty();

		form(tenant, tenancy, "{}").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("home"));
	}

	@Test
	void theSixtyDayLockStillHolds() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy recent = data.tenancy(home(landlord), tenant, TODAY.minusDays(30), null, TenancyStatus.ACTIVE);

		form(tenant, recent, full(4, 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("review_window_not_open"));
		assertThat(reviews.findByTenancyId(recent.getId())).isEmpty();
	}

	@Test
	void aFormReviewNeedsNoTextButAPlainOneKeepsTheThirtyCharacterRule() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);

		mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/reviews")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"direction": "TENANT_TO_PROPERTY", "ratingOverall": 4, "body": "Too short"}"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("body"))
			.andExpect(jsonPath("$.errors[0].code").value("too_short"));
		// the plain endpoint takes no form fields, so they do not make it a form review
		mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/reviews")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"direction": "TENANT_TO_PROPERTY", "ratingOverall": 4, "body": "Too short",
					 "criteria": {"WATER_SUPPLY": 5}}"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("too_short"));
		String json = mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/reviews")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"direction": "TENANT_TO_PROPERTY", "ratingOverall": 4,
					 "body": "Good water and a quiet street all year."}"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.formReview").doesNotExist())
			.andExpect(jsonPath("$.criteria").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		mvc.perform(patch("/reviews/" + JsonPath.read(json, "$.id")).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"ratingOverall": 4, "body": "Good water"}"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("too_short"));
	}

	@Test
	void aFormReviewWithNoParametersKeepsItsOptionalCommentOnEdit() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String json = form(tenant, tenancy, """
			{"home": {"ratingOverall": 4, "body": "Good water and a quiet street all year."}}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.home.formReview").value(true))
			.andExpect(jsonPath("$.home.criteria").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(json, "$.home.id");

		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"ratingOverall": 4, "body": "Good water"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.formReview").value(true))
			.andExpect(jsonPath("$.body").value("Good water"));
		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"ratingOverall": 3, "body": ""}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body").doesNotExist());
	}

	@Test
	void anEditKeepsTheParametersItLeavesOutAndKeepsTheOldVersion() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String json = form(tenant, tenancy, full(4, 4)).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String landlordReview = JsonPath.read(json, "$.landlord.id");

		// an editor that only knows the overall rating and the text does not drop the parameters
		mvc.perform(patch("/reviews/" + landlordReview).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"ratingOverall": 3, "body": "Slow to answer in the last months."}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ratingOverall").value(3))
			.andExpect(jsonPath("$.criteria.PRIVACY").value(4))
			.andExpect(jsonPath("$.wouldRecommend").value(true));
		// the edit endpoint takes no form fields
		mvc.perform(patch("/reviews/" + landlordReview).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"ratingOverall": 2, "body": "", "criteria": {"RESPONSIVENESS": 1}, "wouldRecommend": false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ratingOverall").value(2))
			.andExpect(jsonPath("$.body").doesNotExist())
			.andExpect(jsonPath("$.criteria.RESPONSIVENESS").value(4))
			.andExpect(jsonPath("$.wouldRecommend").value(true));
		assertThat(jdbc.queryForList("select criteria from review_revisions order by revision_no", String.class))
			.containsExactly("RESPONSIVENESS=4,DEPOSIT_RETURN=4,TRANSPARENCY=4,PRIVACY=4,BEHAVIOUR=4",
				"RESPONSIVENESS=4,DEPOSIT_RETURN=4,TRANSPARENCY=4,PRIVACY=4,BEHAVIOUR=4");
	}

	private static final String TENANT_ALL = """
		{"RENT_ON_TIME": %1$d, "CARE_OF_HOME": %1$d, "COMMUNICATION": %1$d, "HOUSE_RULES": %1$d, "MOVE_OUT": %1$d}""";

	private static String tenantPart(int rating) {
		return """
			{"tenant": {"ratingOverall": %d, "criteria": %s, "wouldRecommend": true,
			            "body": "Paid on time every month and kept the home clean."}}
			""".formatted(rating, TENANT_ALL.formatted(rating));
	}

	@Test
	void theOwnerReviewsTheTenantWithTheTenantPartBehindTheReveal() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);

		mvc.perform(get("/me/review-tasks").header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].direction").value("LANDLORD_TO_TENANT"))
			.andExpect(jsonPath("$[0].counterparty.shortName").value("Tara T."));

		String json = form(landlord, tenancy, tenantPart(4))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.home").doesNotExist())
			.andExpect(jsonPath("$.landlord").doesNotExist())
			.andExpect(jsonPath("$.tenant.direction").value("LANDLORD_TO_TENANT"))
			.andExpect(jsonPath("$.tenant.status").value("AWAITING_REVEAL"))
			.andExpect(jsonPath("$.tenant.formReview").value(true))
			.andExpect(jsonPath("$.tenant.criteria.RENT_ON_TIME").value(4))
			.andExpect(jsonPath("$.tenant.criteria.MOVE_OUT").value(4))
			.andExpect(jsonPath("$.tenant.wouldRecommend").value(true))
			.andReturn().getResponse().getContentAsString();
		Review review = reviews.findByPublicId(UUID.fromString(JsonPath.read(json, "$.tenant.id"))).orElseThrow();
		assertThat(review.getCriteria()).hasSize(5).containsEntry(ReviewCriterion.HOUSE_RULES, 4);
		assertThat(review.getSubjectUser().getId()).isEqualTo(tenant.getId());
		mvc.perform(get("/me/review-tasks").header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$", hasSize(0)));

		// the tenant never sees it, not even as waiting (D-175); their landlord review reveals both (D-46)
		mvc.perform(get("/me/reviews/received").param("as", "TENANT").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.items", hasSize(0)));
		form(tenant, tenancy, full(4, 5)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.landlord.status").value("PUBLISHED"));
		mvc.perform(get("/me/reviews/received").param("as", "TENANT").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.items", hasSize(0)));
		mvc.perform(get("/users/" + tenant.getPublicId()).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.tenantRating.reviewCount").value(1));
		mvc.perform(get("/users/" + tenant.getPublicId() + "/reviews").param("as", "TENANT")
				.header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.items[0].status").value("PUBLISHED"))
			.andExpect(jsonPath("$.items[0].criteria.CARE_OF_HOME").value(4))
			.andExpect(jsonPath("$.items[0].body").value("Paid on time every month and kept the home clean."));
	}

	@Test
	void theTenantPartTakesNoProsOrConsAndOnlyTheOwnerWritesIt() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);

		form(landlord, tenancy, """
			{"tenant": {"ratingOverall": 4, "criteria": {"RENT_ON_TIME": 4, "PRIVACY": 3},
			            "pros": "Quiet", "cons": "Late once"}}
			""")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("tenant.criteria.PRIVACY", "tenant.pros",
				"tenant.cons")))
			.andExpect(jsonPath("$.errors[?(@.field == 'tenant.pros')].code").value("not_allowed"))
			.andExpect(jsonPath("$.errors[?(@.field == 'tenant.criteria.PRIVACY')].code").value("wrong_direction"));
		form(landlord, tenancy, "{}").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("tenant"));
		// each side writes only its own parts (D-37)
		form(landlord, tenancy, homeOnly(4)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("wrong_direction"));
		form(tenant, tenancy, tenantPart(4)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("wrong_direction"));
		assertThat(reviews.findByTenancyId(tenancy.getId())).isEmpty();
	}

	@Test
	void aTenancyWithNoLinkedTenantGivesAnUnnamedTaskAndAReviewThatCountsNowhere() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		Tenancy tenancy = active(home(landlord), null);

		mvc.perform(get("/me/review-tasks").header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].direction").value("LANDLORD_TO_TENANT"))
			.andExpect(jsonPath("$[0].counterparty.id").doesNotExist())
			.andExpect(jsonPath("$[0].counterparty.shortName").value("Your tenant"));

		String json = form(landlord, tenancy, tenantPart(2)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.tenant.status").value("AWAITING_REVEAL"))
			.andExpect(jsonPath("$.tenant.subject").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(json, "$.tenant.id");
		// edit and withdraw run the recompute, which skips a review with no subject
		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"ratingOverall": 3, "body": "Paid late twice in the first year."}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ratingOverall").value(3));
		recomputer.recomputeAll();
		mvc.perform(delete("/reviews/" + id)
				.header("Authorization", support.bearer(landlord)))
			.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from rating_aggregates where subject_type = 'TENANT'",
			Integer.class)).isZero();
	}

	@Test
	void parameterAveragesShowFromThreeRatings() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		Property property = home(landlord);
		int[] ratings = {5, 4, 3};
		for (int i = 0; i < ratings.length; i++) {
			User tenant = data.approved("Tenant " + (char) ('A' + i), Role.TENANT);
			LocalDate start = TODAY.minusYears(6 - 2L * i);
			Tenancy tenancy = data.tenancy(property, tenant, start, start.plusYears(1), TenancyStatus.ENDED);
			form(tenant, tenancy, homeOnly(ratings[i])).andExpect(status().isCreated());
			if (i == 1) {
				mvc.perform(get("/properties/" + property.getPublicId() + "/rating"))
					.andExpect(jsonPath("$.criteria", hasSize(9)))
					.andExpect(jsonPath("$.criteria[0].criterion").value("WATER_SUPPLY"))
					.andExpect(jsonPath("$.criteria[0].ratingCount").value(2))
					.andExpect(jsonPath("$.criteria[0].average").doesNotExist());
			}
		}

		mvc.perform(get("/properties/" + property.getPublicId() + "/rating"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewCount").value(3))
			.andExpect(jsonPath("$.average").value(4.0))
			.andExpect(jsonPath("$.criteria", hasSize(9)))
			.andExpect(jsonPath("$.criteria[5].criterion").value("PARKING"))
			.andExpect(jsonPath("$.criteria[5].ratingCount").value(3))
			.andExpect(jsonPath("$.criteria[5].average").value(4.0))
			.andExpect(jsonPath("$.recommend.answered").value(3))
			.andExpect(jsonPath("$.recommend.yes").value(2));
	}

	@Test
	void theLandlordProfileShowsTheLandlordParametersOnceRevealed() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		form(tenant, tenancy, full(4, 5)).andExpect(status().isCreated());

		mvc.perform(get("/users/" + landlord.getPublicId()).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.landlordRating.criteria", hasSize(5)))
			.andExpect(jsonPath("$.landlordRating.criteria[0].ratingCount").value(0))
			.andExpect(jsonPath("$.landlordRating.recommend.answered").value(0));

		// the landlord's own review reveals both (D-46)
		mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/reviews")
				.header("Authorization", support.bearer(landlord)).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"direction": "LANDLORD_TO_TENANT", "ratingOverall": 5,
					 "body": "Paid on time every month and kept the home in good shape."}"""))
			.andExpect(status().isCreated());
		mvc.perform(get("/users/" + landlord.getPublicId()).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.landlordRating.reviewCount").value(1))
			.andExpect(jsonPath("$.landlordRating.criteria[0].criterion").value("RESPONSIVENESS"))
			.andExpect(jsonPath("$.landlordRating.criteria[0].ratingCount").value(1))
			.andExpect(jsonPath("$.landlordRating.criteria[0].average").doesNotExist())
			.andExpect(jsonPath("$.landlordRating.recommend.yes").value(1));
	}

}
