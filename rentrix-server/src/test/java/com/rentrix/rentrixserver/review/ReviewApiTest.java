package com.rentrix.rentrixserver.review;

import com.jayway.jsonpath.JsonPath;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.rating.RatingRecomputer;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.AdminConsole;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.EvidenceLevel;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.verification.VerificationState;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reviews and rating aggregates (PLAN B6): trust rules D-37–D-45, reveal D-46, D-47, aggregates D-33, D-52, D-53. */
@IntegrationTest
// The rating math here runs over reviews without the green tick, so it counts every published review
// ("all"); the default "verified" headline (D-163) is covered by RatingHeadlineModeTest.
@TestPropertySource(properties = "app.ratings.headline=all")
@Transactional
class ReviewApiTest {
	
	@Autowired
	jakarta.persistence.EntityManager entityManager;

	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
	private static final String GOOD = "Clean home, fair landlord and a quick reply to every repair request we made.";

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	ReviewRepository reviews;

	@Autowired
	RatingAggregateRepository aggregates;

	@Autowired
	TenancyRepository tenancies;

	@Autowired
	UserRepository users;

	@Autowired
	ReviewService service;

	@Autowired
	RatingRecomputer recomputer;
	
	@Autowired
	AccessTokenService tokens;

	@Autowired
	JdbcTemplate jdbc;

	// ------------------------------------------------------------ helpers

	/** An approved user whose account is old enough for a first review (D-45). */
	private User approved(String name, Role role) {
		return aged(data.approved(name, role));
	}

	/** Revokes the user's verified phone (as a phone change does, D-101), so the gate (D-132) refuses writes. */
	/** Revokes the phone in the database and drops the test transaction's cached copy of the row. */
	private void revokePhone(User user) {
		entityManager.flush();
		jdbc.update("update identity_verifications set status = 'REVOKED' where user_id = ? and method = 'PHONE_OTP'",
			user.getId());
		entityManager.clear();
	}

	private User aged(User user) {
		Instant created = Instant.now().minus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MILLIS);
		user.setCreatedAt(created);
		jdbc.update("update users set created_at = ? where id = ?", java.sql.Timestamp.from(created), user.getId());
		return user;
	}

	private Property home(User landlord) {
		Property property = data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED);
		property.setUnitLabel("C-1204");
		return property;
	}

	/** A current tenancy that started a year ago, so the review window is open (D-39). */
	private Tenancy active(Property property, User tenant) {
		return data.tenancy(property, tenant, TODAY.minusYears(1), null, TenancyStatus.ACTIVE);
	}

	private Tenancy ended(Property property, User tenant, LocalDate start, LocalDate end) {
		Tenancy tenancy = data.tenancy(property, tenant, start, end, TenancyStatus.ENDED);
		tenancy.setEndedAt(end.atStartOfDay().toInstant(ZoneOffset.UTC));
		return tenancies.save(tenancy);
	}

	private static String review(ReviewDirection direction, int rating, String body) {
		return """
			{"direction": "%s", "ratingOverall": %d, "ratingA": %d, "body": "%s"}
			""".formatted(direction, rating, rating, body);
	}

	private ResultActions write(User author, Tenancy tenancy, ReviewDirection direction, int rating, String body)
		throws Exception {
		return mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/reviews")
			.header("Authorization", support.bearer(author))
			.contentType(MediaType.APPLICATION_JSON).content(review(direction, rating, body)));
	}

	private String written(User author, Tenancy tenancy, ReviewDirection direction, int rating) throws Exception {
		String json = write(author, tenancy, direction, rating, GOOD).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private Review stored(String id) {
		return reviews.findByPublicId(UUID.fromString(id)).orElseThrow();
	}

	private RatingAggregate aggregate(RatingSubjectType type, Long id) {
		return aggregates.findById(new RatingAggregateId(type, id)).orElse(null);
	}

	// ---------------------------------------------------------- trust rules

	@Test
	void propertyReviewPublishesAtOnceAndPersonReviewsRevealTogether() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);

		write(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4, GOOD)
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.author.label").value("Verified tenant")) // never the year (D-169)
			.andExpect(jsonPath("$.author.longStay").value(true)) // a year into the stay: "Stayed 1+ yr"
			.andExpect(jsonPath("$.author.shortName").value("Tara T.")) // the author sees their own name (D-168)
			.andExpect(jsonPath("$.author.id").doesNotExist())
			.andExpect(jsonPath("$.author.badges.id").value(true)) // a verified phone is one of the methods
			.andExpect(jsonPath("$.author.badges.google").value(false))
			.andExpect(jsonPath("$.author.badges.agreement").doesNotExist())
			// the author sees their own stay and times; others do not (D-169)
			.andExpect(jsonPath("$.period.from").value(TODAY.minusYears(1).toString().substring(0, 7)))
			.andExpect(jsonPath("$.publishedAt").exists())
			.andExpect(jsonPath("$.publishedMonth").value(TODAY.toString().substring(0, 7)))
			.andExpect(jsonPath("$.property.unitLabel").doesNotExist());
		assertThat(aggregate(RatingSubjectType.PROPERTY, tenancy.getProperty().getId()).getReviewCount()).isEqualTo(1);

		String first = written(tenant, tenancy, ReviewDirection.TENANT_TO_LANDLORD, 5);
		assertThat(stored(first).getStatus()).isEqualTo(ReviewStatus.AWAITING_REVEAL);
		assertThat(aggregate(RatingSubjectType.LANDLORD, landlord.getId())).as("hidden reviews do not count").isNull();
		// the subject cannot read it, and sees only a bare pending entry plus a review task with otherSideWaiting
		mvc.perform(get("/reviews/" + first).header("Authorization", support.bearer(landlord)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/me/reviews/received").header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].status").value("AWAITING_REVEAL"))
			.andExpect(jsonPath("$.items[0].body").doesNotExist())
			.andExpect(jsonPath("$.items[0].ratingOverall").doesNotExist());
		mvc.perform(get("/me/review-tasks").header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].direction").value("LANDLORD_TO_TENANT"))
			.andExpect(jsonPath("$[0].counterparty.shortName").value("Tara T."))
			.andExpect(jsonPath("$[0].otherSideWaiting").value(true));

		// the counter-review reveals both (D-46)
		write(landlord, tenancy, ReviewDirection.LANDLORD_TO_TENANT, 4, GOOD)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.author.label").value("Owner"))
			.andExpect(jsonPath("$.author.id").doesNotExist());
		assertThat(stored(first).getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
		assertThat(aggregate(RatingSubjectType.LANDLORD, landlord.getId()).getReviewCount()).isEqualTo(1);
		assertThat(aggregate(RatingSubjectType.TENANT, tenant.getId()).getReviewCount()).isEqualTo(1);
		mvc.perform(get("/me/review-tasks").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void hiddenReviewRevealsAloneAfterFourteenDays() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String id = written(landlord, tenancy, ReviewDirection.LANDLORD_TO_TENANT, 3);

		assertThat(service.revealDue()).isZero();
		Review review = stored(id);
		review.setSubmittedAt(Instant.now().minus(Duration.ofDays(15)));
		reviews.saveAndFlush(review);
		assertThat(service.revealDue()).isEqualTo(1);
		assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
		assertThat(aggregate(RatingSubjectType.TENANT, tenant.getId()).getReviewCount()).isEqualTo(1);

		// the tenant's later review publishes at once: the other one is already out
		write(tenant, tenancy, ReviewDirection.TENANT_TO_LANDLORD, 4, GOOD)
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	@Test
	void wrongDirectionIs403AndStrangerIs404() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		User stranger = approved("Sam Stranger", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);

		write(landlord, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4, GOOD)
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("wrong_direction"));
		write(tenant, tenancy, ReviewDirection.LANDLORD_TO_TENANT, 4, GOOD)
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("wrong_direction"));
		write(stranger, tenancy, ReviewDirection.TENANT_TO_LANDLORD, 4, GOOD).andExpect(status().isNotFound());
	}

	@Test
	void authorWithoutAVerifiedMethodPostsAndReadsTheText() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4);

		User pending = aged(data.verified("Pia Pending", Role.TENANT));
		Tenancy own = active(home(landlord), pending);
		// No phone, Aadhaar or LinkedIn verified: the review posts, without the green tick (D-163).
		write(pending, own, ReviewDirection.TENANT_TO_PROPERTY, 4, GOOD)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.verification").value("UNVERIFIED"));

		mvc.perform(get("/properties/" + tenancy.getProperty().getPublicId() + "/reviews")
				.header("Authorization", support.bearer(pending)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].body").value(GOOD))
			.andExpect(jsonPath("$.items[0].submittedAt").doesNotExist());
		// anonymous: home reviews are public with full text and ratings (D-161), but no actions or private dates
		mvc.perform(get("/properties/" + tenancy.getProperty().getPublicId() + "/reviews"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].body").value(GOOD))
			.andExpect(jsonPath("$.items[0].ratingOverall").value(4))
			.andExpect(jsonPath("$.items[0].submittedAt").doesNotExist())
			.andExpect(jsonPath("$.items[0].actions.edit").value(false))
			.andExpect(jsonPath("$.items[0].actions.withdraw").value(false));
		mvc.perform(get("/properties/" + tenancy.getProperty().getPublicId() + "/rating"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewCount").value(1))
			.andExpect(jsonPath("$.average").doesNotExist());
	}

	@Test
	void duplicateIs409AndWithdrawalIsFinalAndNotGated() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String id = written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 2);
		write(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 5, GOOD)
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("review_exists"));

		// the landlord, the subject's side, can neither edit nor withdraw it (D-42)
		mvc.perform(delete("/reviews/" + id).header("Authorization", support.bearer(landlord)))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_author"));

		revokePhone(tenant); // can no longer post: withdrawal is a safety exit (D-91)
		mvc.perform(delete("/reviews/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isNoContent());
		assertThat(stored(id).getStatus()).isEqualTo(ReviewStatus.WITHDRAWN);
		assertThat(aggregate(RatingSubjectType.PROPERTY, tenancy.getProperty().getId()).getReviewCount()).isZero();
		mvc.perform(get("/reviews/" + id).header("Authorization", support.bearer(tenant))).andExpect(status().isNotFound());
		data.verifyPhone(tenant);
		write(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 5, GOOD)
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("review_exists"));
	}

	@Test
	void reviewWindowNeverClosesButMustBeOpen() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Property property = home(landlord);
		// open model (firstmate 2026-11-23): a tenancy that ended 2 years ago still takes a new review
		Tenancy old = ended(property, tenant, TODAY.minusYears(3), TODAY.minusYears(2));
		write(tenant, old, ReviewDirection.TENANT_TO_LANDLORD, 4, GOOD).andExpect(status().isCreated());

		Tenancy fresh = data.tenancy(home(landlord), tenant, TODAY.minusDays(30), null, TenancyStatus.ACTIVE);
		write(tenant, fresh, ReviewDirection.TENANT_TO_LANDLORD, 4, GOOD)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("review_window_not_open"));

		Tenancy pending = data.tenancy(home(landlord), tenant, TODAY.minusYears(1), null,
			TenancyStatus.PENDING_CONFIRMATION);
		write(tenant, pending, ReviewDirection.TENANT_TO_LANDLORD, 4, GOOD)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("tenancy_not_confirmed"));

		// ended 10 days ago after 30 days: the end opens the window before day 60
		Tenancy shortStay = ended(home(landlord), tenant, TODAY.minusDays(40), TODAY.minusDays(10));
		write(tenant, shortStay, ReviewDirection.TENANT_TO_LANDLORD, 4, GOOD).andExpect(status().isCreated());
	}

	@Test
	void contentRulesAndAccountGuards() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);

		write(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4, "Too short.")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[*].code", hasItem("too_short")));
		write(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4,
			"Great place, call the owner on +91 98450 12345 or mail owner@example.test for details.")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[*].code", hasItem("contains_phone")))
			.andExpect(jsonPath("$.errors[*].code", hasItem("contains_email")));
		write(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4,
			"We lived in c-1204 for a year, see more on www.example.test and it was a good time.")
			.andExpect(jsonPath("$.errors[*].code", hasItem("contains_unit_number")))
			.andExpect(jsonPath("$.errors[*].code", hasItem("contains_url")));
		write(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 9, GOOD)
			.andExpect(status().isUnprocessableContent());

		// D-141: the verified phone replaces the old 7-day wait, so a brand-new account reviews at once.
		User fresh = data.approved("Fay Fresh", Role.TENANT);
		Tenancy own = active(home(landlord), fresh);
		write(fresh, own, ReviewDirection.TENANT_TO_PROPERTY, 4, GOOD)
			.andExpect(status().isCreated());
	}

	@Test
	void tenReviewsPerDayThen429() throws Exception {
		User tenant = approved("Tara Tenant", Role.TENANT);
		for (int i = 0; i < ReviewService.MAX_PER_DAY / 2; i++) {
			Tenancy tenancy = active(home(approved("Landlord " + i, Role.LANDLORD)), tenant);
			written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4);
			written(tenant, tenancy, ReviewDirection.TENANT_TO_LANDLORD, 4);
		}
		Tenancy one = active(home(approved("Landlord X", Role.LANDLORD)), tenant);
		write(tenant, one, ReviewDirection.TENANT_TO_PROPERTY, 4, GOOD)
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
	}

	// --------------------------------------------------------- edit + reply

	@Test
	void authorEditsAfterTheSevenDaysAndLosesTheTick() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String id = written(tenant, tenancy, ReviewDirection.TENANT_TO_LANDLORD, 4);
		String edit = """
			{"ratingOverall": 2, "body": "Water supply got worse over the year and the lift stopped working often."}""";

		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content(edit))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ratingOverall").value(2))
			.andExpect(jsonPath("$.editedAt").exists())
			.andExpect(jsonPath("$.actions.edit").value(true));

		// D-156: the 7-day window no longer limits the author; a verified review drops back to the queue
		Review review = stored(id);
		review.setEditUntil(Instant.now().minus(Duration.ofDays(1)));
		review.setSubmittedAt(Instant.now().minus(Duration.ofDays(30)));
		review.setVerificationStatus(VerificationState.VERIFIED);
		reviews.saveAndFlush(review);
		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content(edit.replace("\"ratingOverall\": 2", "\"ratingOverall\": 1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ratingOverall").value(1))
			.andExpect(jsonPath("$.verification").value("UNVERIFIED"))
			.andExpect(jsonPath("$.actions.edit").value(true));
		assertThat(stored(id).getVerificationStatus()).isEqualTo(VerificationState.UNVERIFIED);
		assertThat(jdbc.queryForObject("select count(*) from review_revisions where review_id = ?", Integer.class,
			review.getId())).isEqualTo(2);
	}

	@Test
	void publishedPropertyReviewIsEditableAndRecomputesTheRating() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String id = written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4);
		Review review = stored(id);
		review.setVerificationStatus(VerificationState.VERIFIED);
		reviews.saveAndFlush(review);

		mvc.perform(get("/reviews/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.actions.edit").value(true));
		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"ratingOverall": 1, "body": "Water supply got worse over the year and the lift stopped working often."}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.editedAt").exists())
			.andExpect(jsonPath("$.verification").value("UNVERIFIED"));
		assertThat(aggregate(RatingSubjectType.PROPERTY, tenancy.getProperty().getId()).getRatingSum()).isEqualTo(1);
		assertThat(jdbc.queryForObject(
			"select count(*) from edit_history where target_type = 'REVIEW' and target_ref = ? and was_verified",
			Integer.class, id)).isPositive();
	}

	@Test
	void personReviewStaysEditableOnceRevealed() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String id = written(landlord, tenancy, ReviewDirection.LANDLORD_TO_TENANT, 4);
		String edit = """
			{"ratingOverall": 3, "body": "Paid rent on time most months but left the kitchen in poor shape."}""";

		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(edit))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ratingOverall").value(3))
			.andExpect(jsonPath("$.actions.edit").value(true));

		written(tenant, tenancy, ReviewDirection.TENANT_TO_LANDLORD, 2);
		assertThat(stored(id).getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
		mvc.perform(get("/reviews/" + id).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.actions.edit").value(true));
		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(edit.replace("\"ratingOverall\": 3", "\"ratingOverall\": 1")))
			.andExpect(status().isOk());
		assertThat(aggregate(RatingSubjectType.TENANT, tenant.getId()).getRatingSum()).isEqualTo(1);

		mvc.perform(delete("/reviews/" + id).header("Authorization", support.bearer(landlord)))
			.andExpect(status().is2xxSuccessful());
		mvc.perform(patch("/reviews/" + id).header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(edit))
			.andExpect(status().is4xxClientError());
	}

	@Test
	void reviewerNamesNeverReachOtherViewersOnlyLabelsAndBadges() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		User stranger = approved("Sam Stranger", Role.TENANT);
		tenant.setGoogleSub("google-sub-tara");
		users.saveAndFlush(tenant);
		Tenancy tenancy = active(home(landlord), tenant);
		tenancy.setEvidenceLevel(EvidenceLevel.DOCUMENT);
		tenancies.saveAndFlush(tenancy);
		String id = written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4);
		mvc.perform(post("/reviews/" + id + "/response").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"Thanks, the pump was replaced.\"}"))
			.andExpect(status().isCreated());
		String propertyId = tenancy.getProperty().getPublicId().toString();
		String label = "Verified tenant";

		// D-168: a signed-out reader and another signed-in user get labels and badges, never a name or a link
		for (String bearer : new String[] {null, support.bearer(stranger)}) {
			for (String path : new String[] {"/properties/" + propertyId + "/reviews", "/reviews/" + id}) {
				var request = get(path);
				if (bearer != null) {
					request.header("Authorization", bearer);
				} else if (path.startsWith("/reviews/")) {
					continue; // a single review needs a session
				}
				String json = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
					.getContentAsString();
				assertThat(json).doesNotContain("Tara", "Lata", "shortName", "authorShortName");
				String root = path.endsWith("/reviews") ? "$.items[0]" : "$";
				assertThat(JsonPath.<String>read(json, root + ".author.label")).isEqualTo(label);
				assertThat(JsonPath.<Boolean>read(json, root + ".author.badges.google")).isTrue();
				assertThat(JsonPath.<Boolean>read(json, root + ".author.badges.id")).isTrue();
				assertThat(JsonPath.<Boolean>read(json, root + ".author.badges.agreement")).isTrue();
				assertThat(JsonPath.<String>read(json, root + ".response.authorLabel")).isEqualTo("Owner");
				assertThat(json).doesNotContain(tenant.getPublicId().toString());
			}
			var page = get("/properties/" + propertyId);
			if (bearer != null) {
				page.header("Authorization", bearer);
			}
			String detail = mvc.perform(page).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
			assertThat(detail).doesNotContain("Lata", "shortName");
			assertThat(JsonPath.<String>read(detail, "$.landlord.label")).isEqualTo("Owner");
			assertThat(JsonPath.<Boolean>read(detail, "$.landlord.badges.id")).isTrue();
		}
		// the author still sees their own name on their own pages
		mvc.perform(get("/me/reviews/written").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.items[0].author.shortName").value("Tara T."))
			.andExpect(jsonPath("$.items[0].author.label").value(label));
		// no agreement status on the tenancy: no agreement badge slot at all
		tenancy.setEvidenceLevel(EvidenceLevel.MUTUAL);
		tenancies.saveAndFlush(tenancy);
		mvc.perform(get("/properties/" + propertyId + "/reviews"))
			.andExpect(jsonPath("$.items[0].author.badges.agreement").doesNotExist());
	}

	/** Keys only the author and admins get (D-169): the stay and every exact time. */
	private static final String[] PRIVATE_KEYS = {"\"period\"", "\"submittedAt\"", "\"publishedAt\"",
		"\"editedAt\"", "\"editUntil\"", "\"createdAt\"", "\"lastReviewAt\""};
	
	/** A full date or an exact time ("2025-11-26", "2025-11-26T10:00:00Z"); a "2025-11" month is allowed. */
	private static final java.util.regex.Pattern DAY_OR_TIME = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
	
	private String json(String path, String bearer) throws Exception {
		var request = get(path);
		if (bearer != null) {
			request.header("Authorization", bearer);
		}
		return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}
	
	@Test
	void noReviewResponseGivesANonAdminTheStayDatesOrExactTimes() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		User stranger = approved("Sam Stranger", Role.TENANT);
		User admin = data.user("Ada Admin", Role.ADMIN);
		LocalDate start = TODAY.minusYears(2).withDayOfMonth(3);
		LocalDate end = TODAY.minusMonths(2).withDayOfMonth(20);
		Tenancy tenancy = ended(home(landlord), tenant, start, end);
		String home = written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4);
		String aboutLandlord = written(tenant, tenancy, ReviewDirection.TENANT_TO_LANDLORD, 3);
		String aboutTenant = written(landlord, tenancy, ReviewDirection.LANDLORD_TO_TENANT, 5); // reveals both (D-46)
		mvc.perform(post("/reviews/" + home + "/response").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"Thanks, the pump was replaced.\"}"))
			.andExpect(status().isCreated());
		mvc.perform(patch("/reviews/" + home).header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content("""
					{"ratingOverall": 4, "body": "Water supply got better over the year and the lift works every day now."}"""))
			.andExpect(status().isOk());
		String propertyId = tenancy.getProperty().getPublicId().toString();
		String month = TODAY.toString().substring(0, 7);
		
		// A review id is random (v4), never a UUIDv7 that carries the time it was written.
		for (String id : new String[] {home, aboutLandlord, aboutTenant}) {
			assertThat(UUID.fromString(id).version()).isEqualTo(4);
		}
		
		// Signed out, another signed-in user, and the reviewed landlord himself (D-169).
		String[] viewers = {null, support.bearer(stranger), support.bearer(landlord)};
		for (String bearer : viewers) {
			boolean signedIn = bearer != null;
			boolean isLandlord = bearer != null && bearer.equals(viewers[2]);
			java.util.List<String> paths = new java.util.ArrayList<>(java.util.List.of(
				"/properties/" + propertyId + "/reviews", "/properties/" + propertyId + "/rating"));
			if (signedIn) {
				paths.addAll(java.util.List.of("/reviews/" + home, "/reviews/" + aboutLandlord,
					"/users/" + landlord.getPublicId() + "/reviews", "/users/" + landlord.getPublicId()));
			}
			if (isLandlord) {
				// Not his own review of the tenant (GET /reviews/{id}, /users/{tenant}/reviews?as=TENANT): as its
				// author he sees its stay and times.
				paths.add("/me/reviews/received?as=LANDLORD");
			}
			for (String path : paths) {
				String json = json(path, bearer);
				if (isLandlord) {
					// The landlord's own reply keeps its own times; they are not the reviewer's.
					json = json.replaceAll("\"createdAt\":\"[^\"]+\",\"editUntil\":\"[^\"]+\"", "");
				}
				assertThat(json).as(path).doesNotContain(PRIVATE_KEYS)
					.doesNotContain("Tenant, ", String.valueOf(start), String.valueOf(end), "currently");
				assertThat(DAY_OR_TIME.matcher(json).find()).as(path + " has a day or a time: " + json).isFalse();
			}
			String reviewsJson = json("/properties/" + propertyId + "/reviews", bearer);
			assertThat(JsonPath.<String>read(reviewsJson, "$.items[0].author.label")).isEqualTo("Verified tenant");
			assertThat(JsonPath.<Boolean>read(reviewsJson, "$.items[0].author.longStay")).isTrue();
			assertThat(JsonPath.<String>read(reviewsJson, "$.items[0].publishedMonth")).isEqualTo(month);
			assertThat(JsonPath.<Boolean>read(reviewsJson, "$.items[0].edited")).isTrue();
			assertThat(JsonPath.<String>read(json("/properties/" + propertyId + "/rating", bearer),
				"$.lastReviewMonth")).isEqualTo(month);
		}
		
		// The search card carries no review date either.
		String search = json("/properties?city=BLR", null);
		assertThat(search).doesNotContain("\"period\"", "\"lastReviewAt\"", String.valueOf(start), String.valueOf(end));
		
		// The posting order is not public: no "newest" sort.
		mvc.perform(get("/properties/" + propertyId + "/reviews").param("sort", "newest"))
			.andExpect(status().isUnprocessableEntity());
		
		// The author still sees their stay and times on their own pages; admins see everything.
		mvc.perform(get("/me/reviews/written").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.items[0].period.from").value(start.toString().substring(0, 7)))
			.andExpect(jsonPath("$.items[0].period.to").value(end.toString().substring(0, 7)))
			.andExpect(jsonPath("$.items[0].submittedAt").exists());
		// An admin reads it in the admin console (§13.3): its host and an admin session.
		mvc.perform(get("/reviews/" + home).with(AdminConsole.adminHost())
				.header("Authorization", AdminConsole.adminBearer(tokens, admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.period.from").value(start.toString().substring(0, 7)))
			.andExpect(jsonPath("$.publishedAt").exists())
			.andExpect(jsonPath("$.editedAt").exists())
			.andExpect(jsonPath("$.response.createdAt").exists());
	}
	
	@Test
	void aOneSidedStayIsPlainTenantAndAShortOneHasNoLongStayBadge() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = ended(home(landlord), tenant, TODAY.minusMonths(8), TODAY.minusMonths(1));
		tenancy.setInitiatedBy(com.rentrix.rentrixserver.tenancy.TenancyParty.TENANT);
		tenancies.saveAndFlush(tenancy);
		String id = written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4);
		
		String json = json("/properties/" + tenancy.getProperty().getPublicId() + "/reviews", null);
		assertThat(JsonPath.<String>read(json, "$.items[0].author.label")).isEqualTo("Tenant");
		assertThat(json).doesNotContain("longStay");
		// The landlord's confirmation makes it "Verified tenant".
		tenancy.setConfirmedAt(Instant.now());
		tenancies.saveAndFlush(tenancy);
		assertThat(JsonPath.<String>read(json("/reviews/" + id, support.bearer(landlord)), "$.author.label"))
			.isEqualTo("Verified tenant");
	}
	
	@Test
	void subjectRespondsOnce() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String id = written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 3);
		String reply = "{\"body\": \"Thanks, the pump was replaced last month.\"}";

		mvc.perform(post("/reviews/" + id + "/response").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON).content(reply))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_subject"));
		mvc.perform(post("/reviews/" + id + "/response").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(reply))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.response.body").value("Thanks, the pump was replaced last month."))
			.andExpect(jsonPath("$.response.authorLabel").value("Owner"))
			.andExpect(jsonPath("$.response.authorShortName").value("Lata L.")) // the responder's own view
			.andExpect(jsonPath("$.actions.respond").value(false))
			.andExpect(jsonPath("$.actions.editResponse").value(true));
		mvc.perform(post("/reviews/" + id + "/response").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(reply))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("response_exists"));
		mvc.perform(patch("/reviews/" + id + "/response").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"Thanks, the pump and lift are fixed.\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.response.body").value("Thanks, the pump and lift are fixed."));
		// Losing the only verified method no longer closes writes (D-163).
		revokePhone(landlord);
		mvc.perform(patch("/reviews/" + id + "/response").header("Authorization", support.bearer(landlord))
				.contentType(MediaType.APPLICATION_JSON).content(reply))
			.andExpect(status().isOk());
	}

	// ----------------------------------------------------------- aggregates

	@Test
	void allHeadlineCountsUnverifiedPublishedReviews() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		String id = written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4);
		Long propertyId = tenancy.getProperty().getId();

		assertThat(stored(id).getVerificationStatus()).isNull();
		RatingAggregate row = aggregate(RatingSubjectType.PROPERTY, propertyId);
		assertThat(row.getReviewCount()).as("app.ratings.headline=all (D-135)").isEqualTo(1);
		assertThat(row.getRatingSum()).isEqualTo(4);
	}

	@Test
	void aggregateMathWorkedExample() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User viewer = aged(data.verified("Vic Viewer", Role.TENANT));
		int[] ratings = {5, 4};
		for (int i = 0; i < ratings.length; i++) {
			Tenancy tenancy = active(home(landlord), approved("Tenant " + i, Role.TENANT));
			Review review = stored(written(tenancy.getTenant(), tenancy, ReviewDirection.TENANT_TO_LANDLORD, ratings[i]));
			review.setSubmittedAt(Instant.now().minus(Duration.ofDays(15)));
		}
		service.revealDue();
		// n < 3: count only, no average (D-52)
		mvc.perform(get("/users/" + landlord.getPublicId()).header("Authorization", support.bearer(viewer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.landlordRating.reviewCount").value(2))
			.andExpect(jsonPath("$.landlordRating.average").doesNotExist())
			.andExpect(jsonPath("$.listedHomes").value(2))
			.andExpect(jsonPath("$.tenantRating").doesNotExist());

		Tenancy third = active(home(landlord), approved("Tenant 2", Role.TENANT));
		Review review = stored(written(third.getTenant(), third, ReviewDirection.TENANT_TO_LANDLORD, 4));
		review.setSubmittedAt(Instant.now().minus(Duration.ofDays(15)));
		service.revealDue();

		// §9: 5, 4, 4 → n=3, average 4.33 (shown 4.3), bayes (5·3.5 + 13) / 8 = 3.813
		RatingAggregate row = aggregate(RatingSubjectType.LANDLORD, landlord.getId());
		assertThat(row.getReviewCount()).isEqualTo(3);
		assertThat(row.getRatingSum()).isEqualTo(13);
		assertThat(row.getAverage()).isEqualByComparingTo("4.33");
		assertThat(row.getBayesScore()).isEqualByComparingTo("3.813");
		assertThat(row.getCount4()).isEqualTo(2);
		mvc.perform(get("/users/" + landlord.getPublicId()).header("Authorization", support.bearer(viewer)))
			.andExpect(jsonPath("$.landlordRating.average").value(4.3))
			.andExpect(jsonPath("$.landlordRating.distribution[3]").value(2))
			.andExpect(jsonPath("$.landlordRating.subRatings.a").value(4.3));
		mvc.perform(get("/users/" + landlord.getPublicId() + "/reviews").header("Authorization", support.bearer(viewer)))
			.andExpect(jsonPath("$.items", hasSize(3)))
			.andExpect(jsonPath("$.items[0].author.id").doesNotExist());

		// the nightly repair rebuilds a corrupted row from source (D-33)
		row.setReviewCount(99);
		row.setBayesScore(BigDecimal.ONE);
		aggregates.saveAndFlush(row);
		recomputer.recomputeAll();
		assertThat(aggregate(RatingSubjectType.LANDLORD, landlord.getId()).getReviewCount()).isEqualTo(3);
		assertThat(aggregate(RatingSubjectType.LANDLORD, landlord.getId()).getBayesScore()).isEqualByComparingTo("3.813");
	}

	@Test
	void repeatPairCountsOnlyTheMostRecentTenancy() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Property property = home(landlord);
		Tenancy first = ended(property, tenant, TODAY.minusYears(2), TODAY.minusDays(100));
		Tenancy second = active(property, tenant);

		String older = written(tenant, first, ReviewDirection.TENANT_TO_PROPERTY, 5);
		assertThat(aggregate(RatingSubjectType.PROPERTY, property.getId()).getRatingSum()).isEqualTo(5);
		String newer = written(tenant, second, ReviewDirection.TENANT_TO_PROPERTY, 2);
		assertThat(stored(older).isCountsInAggregate()).isFalse();
		assertThat(stored(newer).isCountsInAggregate()).isTrue();
		RatingAggregate row = aggregate(RatingSubjectType.PROPERTY, property.getId());
		assertThat(row.getReviewCount()).isEqualTo(1);
		assertThat(row.getRatingSum()).isEqualTo(2);

		// both stay visible; withdrawing the newer one lets the older count again
		mvc.perform(get("/properties/" + property.getPublicId() + "/reviews").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.items", hasSize(2)));
		mvc.perform(delete("/reviews/" + newer).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isNoContent());
		assertThat(stored(older).isCountsInAggregate()).isTrue();
		assertThat(aggregate(RatingSubjectType.PROPERTY, property.getId()).getRatingSum()).isEqualTo(5);

		// person reviews of the pair: the same rule
		String olderPerson = written(landlord, first, ReviewDirection.LANDLORD_TO_TENANT, 5);
		Review hidden = stored(olderPerson);
		hidden.setSubmittedAt(Instant.now().minus(Duration.ofDays(15)));
		service.revealDue();
		String newerPerson = written(landlord, second, ReviewDirection.LANDLORD_TO_TENANT, 3);
		stored(newerPerson).setSubmittedAt(Instant.now().minus(Duration.ofDays(15)));
		service.revealDue();
		assertThat(stored(olderPerson).isCountsInAggregate()).isFalse();
		assertThat(aggregate(RatingSubjectType.TENANT, tenant.getId()).getRatingSum()).isEqualTo(3);
	}

	// -------------------------------------------------------------- privacy

	@Test
	void tenantProfileIs404ToStrangersAndOpenToTheirLandlord() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		User stranger = approved("Sam Stranger", Role.LANDLORD);
		Tenancy tenancy = active(home(landlord), tenant);
		String id = written(landlord, tenancy, ReviewDirection.LANDLORD_TO_TENANT, 4);
		stored(id).setSubmittedAt(Instant.now().minus(Duration.ofDays(15)));
		service.revealDue();

		mvc.perform(get("/users/" + tenant.getPublicId()).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/users/" + tenant.getPublicId() + "/reviews?as=TENANT")
				.header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/reviews/" + id).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/users/" + tenant.getPublicId())).andExpect(status().isUnauthorized());

		mvc.perform(get("/users/" + tenant.getPublicId()).header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tenantRating.reviewCount").value(1))
			.andExpect(jsonPath("$.landlordRating").doesNotExist());
		mvc.perform(get("/users/" + tenant.getPublicId() + "/reviews?as=TENANT")
				.header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.items", hasSize(1)));
		mvc.perform(get("/users/" + tenant.getPublicId()).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk());
		// a landlord profile is open to any signed-in user (D-55)
		mvc.perform(get("/users/" + landlord.getPublicId()).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.label").value("Owner"))
			.andExpect(jsonPath("$.shortName").doesNotExist()) // never the name to others (D-168)
			.andExpect(jsonPath("$.email").doesNotExist());
	}

	@Test
	void writtenListsOwnReviewsInEveryVisibleState() throws Exception {
		User landlord = approved("Lata Landlord", Role.LANDLORD);
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy tenancy = active(home(landlord), tenant);
		written(tenant, tenancy, ReviewDirection.TENANT_TO_LANDLORD, 4);
		written(tenant, tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4);
		mvc.perform(get("/me/reviews/written").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[*].status", hasItem("AWAITING_REVEAL")))
			.andExpect(jsonPath("$.items[0].editUntil").exists());
	}

	@Test
	void writtenNarrowsToOneSideForTheModeSwitch() throws Exception {
		// one account on both sides: tenant of one home, landlord of another
		User owner = approved("Lata Landlord", Role.LANDLORD);
		User both = aged(data.approved("Bina Both", Role.TENANT, Role.LANDLORD));
		User tenant = approved("Tara Tenant", Role.TENANT);
		Tenancy renting = active(home(owner), both);
		Tenancy letting = active(home(both), tenant);
		written(both, renting, ReviewDirection.TENANT_TO_PROPERTY, 4);
		written(both, renting, ReviewDirection.TENANT_TO_LANDLORD, 4);
		written(both, letting, ReviewDirection.LANDLORD_TO_TENANT, 5);

		mvc.perform(get("/me/reviews/written").header("Authorization", support.bearer(both)))
			.andExpect(jsonPath("$.items", hasSize(3)));
		mvc.perform(get("/me/reviews/written?as=TENANT").header("Authorization", support.bearer(both)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[*].direction", hasItem("TENANT_TO_PROPERTY")))
			.andExpect(jsonPath("$.items[*].direction", hasItem("TENANT_TO_LANDLORD")));
		mvc.perform(get("/me/reviews/written?as=LANDLORD").header("Authorization", support.bearer(both)))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].direction").value("LANDLORD_TO_TENANT"));
		mvc.perform(get("/me/reviews/written?as=OWNER").header("Authorization", support.bearer(both)))
			.andExpect(status().isBadRequest());
	}

}
