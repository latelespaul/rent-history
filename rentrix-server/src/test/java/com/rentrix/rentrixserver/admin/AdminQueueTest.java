package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.identity.CheckedBy;
import com.rentrix.rentrixserver.identity.IdentityMethod;
import com.rentrix.rentrixserver.identity.IdentityStatus;
import com.rentrix.rentrixserver.identity.IdentityVerification;
import com.rentrix.rentrixserver.identity.IdentityVerificationRepository;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.report.Report;
import com.rentrix.rentrixserver.report.ReportReason;
import com.rentrix.rentrixserver.report.ReportRepository;
import com.rentrix.rentrixserver.report.ReportStatus;
import com.rentrix.rentrixserver.report.ReportTargetType;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.VerificationApplication;
import com.rentrix.rentrixserver.verification.VerificationApplicationRepository;
import com.rentrix.rentrixserver.verification.VerificationState;
import com.rentrix.rentrixserver.verification.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import static com.rentrix.rentrixserver.support.AdminConsole.adminBearer;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The approvals queue (D-134, D-136): what waits, and each answer writing the green-tick state and one audit row
 * (D-87) through the existing decision paths, on the admin host only.
 */
@IntegrationTest
class AdminQueueTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	AccessTokenService tokens;

	@Autowired
	PropertyRepository properties;

	@Autowired
	TenancyRepository tenancies;

	@Autowired
	ReviewRepository reviews;

	@Autowired
	ReportRepository reports;

	@Autowired
	IdentityVerificationRepository checks;

	@Autowired
	VerificationApplicationRepository applications;

	@Autowired
	CityService cities;

	@Autowired
	CityRepository cityRows;

	@Autowired
	ModerationActionRepository audit;

	@Autowired
	TransactionTemplate transactions;
	
	@Autowired
	UserRepository userRows;

	private User admin() {
		return data.user("Queue Admin " + UUID.randomUUID(), Role.ADMIN);
	}

	private ResultActions queue(User admin, String query) throws Exception {
		return mvc.perform(get("/admin/queue" + query).with(adminHost())
			.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)));
	}

	/** {@code POST /admin/<path>} as {@code admin} on the admin host. */
	private ResultActions decide(User admin, String path, String body) throws Exception {
		return mvc.perform(post("/admin/" + path).with(adminHost())
			.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private Tenancy tenancy(User landlord, User tenant) {
		Property home = data.property(landlord, "BLR", "Queue Layout " + UUID.randomUUID(), PropertyStatus.PUBLISHED);
		return data.tenancy(home, tenant, LocalDate.of(2025, 1, 1), null, TenancyStatus.ACTIVE);
	}

	private Review review(Tenancy tenancy) {
		return review(tenancy, ReviewDirection.TENANT_TO_PROPERTY);
	}
	
	/**
	 * Hidden reviews here are of the landlord: a hidden home review would leave a PROPERTY rating row without a score
	 * in the shared test database, which top-rated sorts elsewhere do not expect.
	 */
	private Review review(Tenancy tenancy, ReviewDirection direction) {
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(direction);
		review.setAuthor(tenancy.getTenant());
		review.setSubjectUser(direction == ReviewDirection.TENANT_TO_LANDLORD ? tenancy.getLandlord() : null);
		review.setProperty(tenancy.getProperty());
		review.setRatingOverall(4);
		review.setBody("Quiet street, the water supply was steady all year.");
		review.setStatus(ReviewStatus.PUBLISHED);
		review.setSubmittedAt(Instant.parse("2025-02-01T00:00:00Z"));
		return reviews.save(review);
	}

	private List<ModerationActionType> trail(ModerationTargetType type, Object ref) {
		return audit.findByTargetTypeAndTargetRefOrderByCreatedAtAscIdAsc(type, ref.toString()).stream()
			.map(ModerationAction::getAction).toList();
	}

	private IdentityVerification pendingCheck(User user, IdentityMethod method) {
		IdentityVerification check = new IdentityVerification();
		check.setUser(user);
		check.setMethod(method);
		check.setStatus(IdentityStatus.PENDING);
		check.setCheckedBy(CheckedBy.ADMIN);
		if (method == IdentityMethod.LINKEDIN) {
			check.setLinkedinUrl("https://www.linkedin.com/in/queue-" + UUID.randomUUID());
		} else {
			check.setLast4("234F");
		}
		return checks.save(check);
	}

	@Test
	void homesTenanciesAndReviewsWaitAndGetTheirTickOrARejection() throws Exception {
		User admin = admin();
		User landlord = data.approved("Queue Landlord", Role.LANDLORD);
		User tenant = data.approved("Queue Tenant", Role.TENANT);
		Tenancy tenancy = tenancy(landlord, tenant);
		Property home = tenancy.getProperty();
		Review review = review(tenancy);

		queue(admin, "").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", hasItem(home.getPublicId().toString())))
			.andExpect(jsonPath("$.items[*].id", hasItem(tenancy.getPublicId().toString())))
			.andExpect(jsonPath("$.items[*].id", hasItem(review.getPublicId().toString())))
			.andExpect(jsonPath("$.counts.PROPERTY").isNumber());
		queue(admin, "?type=TENANCY&city=BLR")
			.andExpect(jsonPath("$.items[*].type", not(hasItem("PROPERTY"))))
			.andExpect(jsonPath("$.items[*].id", hasItem(tenancy.getPublicId().toString())));

		// Approve is one call: the home gets the green tick and leaves the queue.
		decide(admin, "properties/" + home.getPublicId() + "/approve", "{}")
			.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("VERIFIED"));
		assertThat(properties.findById(home.getId()).orElseThrow().getVerificationStatus())
			.isEqualTo(VerificationState.VERIFIED);
		assertThat(trail(ModerationTargetType.PROPERTY, home.getPublicId()))
			.containsExactly(ModerationActionType.APPROVE_PROPERTY);
		queue(admin, "?type=PROPERTY").andExpect(jsonPath("$.items[*].id", not(hasItem(home.getPublicId().toString()))));
		mvc.perform(get("/properties/" + home.getPublicId())).andExpect(jsonPath("$.verification").value("VERIFIED"));

		// Reject needs a reason code; the note is optional and kept with it.
		decide(admin, "tenancies/" + tenancy.getPublicId() + "/reject", "{}").andExpect(status().isUnprocessableContent());
		decide(admin, "tenancies/" + tenancy.getPublicId() + "/reject", "{\"code\":\"DETAILS_WRONG\",\"note\":\"Rent looks off\"}")
			.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("REJECTED"));
		Tenancy rejected = tenancies.findById(tenancy.getId()).orElseThrow();
		assertThat(rejected.getVerificationStatus()).isEqualTo(VerificationState.REJECTED);
		assertThat(rejected.getVerificationReason()).isEqualTo("DETAILS_WRONG: Rent looks off");
		assertThat(rejected.getStatus()).as("a rejected tenancy stays on the site").isEqualTo(TenancyStatus.ACTIVE);
		assertThat(trail(ModerationTargetType.TENANCY, tenancy.getPublicId()))
			.containsExactly(ModerationActionType.REJECT_ITEM);

		decide(admin, "reviews/" + review.getPublicId() + "/approve", "{}").andExpect(jsonPath("$.outcome").value("VERIFIED"));
		Review ticked = reviews.findById(review.getId()).orElseThrow();
		assertThat(ticked.getVerificationStatus()).isEqualTo(VerificationState.VERIFIED);
		assertThat(ticked.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
		assertThat(trail(ModerationTargetType.REVIEW, review.getPublicId()))
			.containsExactly(ModerationActionType.VERIFY_ITEM);
		decide(admin, "reviews/" + review.getPublicId() + "/approve", "{}").andExpect(status().isConflict());
		mvc.perform(patch("/admin/cities/BLR").with(adminHost()).header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"live\":false}"))
			.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("action_not_allowed"));
	}

	@Test
	void hideIsForAbuseAndReportsAreDismissedOrActioned() throws Exception {
		User admin = admin();
		User landlord = data.approved("Hide Landlord", Role.LANDLORD);
		User tenant = data.approved("Hide Tenant", Role.TENANT);
		Review abusive = review(tenancy(landlord, tenant), ReviewDirection.TENANT_TO_LANDLORD);
		// Abuse is a hide reason, never a reject reason.
		decide(admin, "reviews/" + abusive.getPublicId() + "/reject", "{\"code\":\"ABUSE\"}")
			.andExpect(status().isUnprocessableContent());
		decide(admin, "reviews/" + abusive.getPublicId() + "/hide", "{\"code\":\"ABUSE\"}")
			.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("HIDDEN"));
		assertThat(reviews.findById(abusive.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.HIDDEN);
		assertThat(trail(ModerationTargetType.REVIEW, abusive.getPublicId()))
			.containsExactly(ModerationActionType.HIDE_REVIEW);
		decide(admin, "reviews/" + abusive.getPublicId() + "/hide", "{\"code\":\"ABUSE\"}")
			.andExpect(status().isConflict());
		decide(admin, "reviews/" + abusive.getPublicId() + "/reject", "{\"code\":\"OTHER\"}")
			.andExpect(status().isConflict());

		Review reported = review(tenancy(landlord, data.approved("Other Tenant", Role.TENANT)),
			ReviewDirection.TENANT_TO_LANDLORD);
		Report report = new Report();
		report.setReporter(landlord);
		report.setTargetType(ReportTargetType.REVIEW);
		report.setTargetId(reported.getId());
		report.setReason(ReportReason.HARASSMENT_OR_HATE);
		report = reports.save(report);
		Report other = new Report();
		other.setReporter(tenant);
		other.setTargetType(ReportTargetType.REVIEW);
		other.setTargetId(reported.getId());
		other.setReason(ReportReason.OFF_TOPIC);
		other = reports.save(other);
		queue(admin, "?type=REPORT").andExpect(jsonPath("$.items[*].id", hasItem(report.getPublicId().toString())));
		// Reports have their own page: the approvals list without a type leaves them out.
		queue(admin, "").andExpect(jsonPath("$.items[*].type", not(hasItem("REPORT"))));

		decide(admin, "reports/" + report.getPublicId() + "/resolve",
				"{\"outcome\":\"HIDE\",\"code\":\"ABUSE\",\"note\":\"Slur in the text\"}")
			.andExpect(jsonPath("$.outcome").value("ACTIONED"));
		assertThat(reports.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.ACTIONED);
		assertThat(reviews.findById(reported.getId()).orElseThrow().getStatus()).isEqualTo(ReviewStatus.HIDDEN);
		// The second report on the now hidden review closes too, without hiding it again.
		decide(admin, "reports/" + other.getPublicId() + "/resolve", "{\"outcome\":\"HIDE\",\"code\":\"OTHER\"}")
			.andExpect(jsonPath("$.outcome").value("ACTIONED"));
		assertThat(trail(ModerationTargetType.REVIEW, reported.getPublicId()))
			.containsExactly(ModerationActionType.HIDE_REVIEW);
		decide(admin, "reports/" + other.getPublicId() + "/resolve", "{\"outcome\":\"DISMISS\"}")
			.andExpect(status().isConflict());
		decide(admin, "reviews/" + reported.getPublicId() + "/approve", "{}").andExpect(status().isConflict());
	}

	@Test
	void aSolePendingIdCheckIsDecidedFromTheQueue() throws Exception {
		User admin = admin();
		// No application at all: before the queue, such a check could never be decided and stayed PENDING.
		User linked = data.verified("Only LinkedIn", Role.TENANT);
		IdentityVerification linkedin = pendingCheck(linked, IdentityMethod.LINKEDIN);
		User panOnly = data.verified("Only PAN", Role.LANDLORD);
		IdentityVerification pan = pendingCheck(panOnly, IdentityMethod.PAN);
		queue(admin, "?type=IDENTITY")
			.andExpect(jsonPath("$.items[*].id", hasItem(linkedin.getPublicId().toString())))
			.andExpect(jsonPath("$.items[*].id", hasItem(pan.getPublicId().toString())));

		decide(admin, "identity/" + linkedin.getPublicId() + "/reject", "{\"code\":\"LINKEDIN_MISMATCH\"}")
			.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("REJECTED"));
		assertThat(checks.findById(linkedin.getId()).orElseThrow().getStatus()).isEqualTo(IdentityStatus.REJECTED);
		assertThat(trail(ModerationTargetType.IDENTITY, linkedin.getPublicId()))
			.containsExactly(ModerationActionType.REJECT_IDENTITY);
		decide(admin, "identity/" + pan.getPublicId() + "/verify", "{}").andExpect(jsonPath("$.outcome").value("VERIFIED"));
		assertThat(checks.findById(pan.getId()).orElseThrow().getStatus()).isEqualTo(IdentityStatus.VERIFIED);
		decide(admin, "identity/" + pan.getPublicId() + "/reject", "{\"code\":\"OTHER\"}").andExpect(status().isConflict());
	}

	@Test
	void aUserApplicationAndANewCityAreDecidedFromTheQueue() throws Exception {
		User admin = admin();
		User applicant = data.verified("Queue Applicant", Role.TENANT);
		data.verifyPhone(applicant);
		VerificationApplication application = new VerificationApplication();
		application.setUser(applicant);
		application.setRole(Role.TENANT);
		application.setStatus(VerificationStatus.PENDING);
		application.setSubmittedAt(Instant.now());
		application = applications.save(application);
		queue(admin, "?type=USER").andExpect(jsonPath("$.items[0].id").value(application.getPublicId().toString()));
		decide(admin, "applications/" + application.getPublicId() + "/approve", "{}").andExpect(status().isOk());
		assertThat(applications.findById(application.getId()).orElseThrow().getStatus())
			.isEqualTo(VerificationStatus.APPROVED);

		String name = "Queuetown " + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
		City city = transactions.execute(s -> cities.findOrCreatePending(name, "Goa"));
		queue(admin, "?type=CITY").andExpect(jsonPath("$.items[*].id", hasItem(city.getCode())));
		mvc.perform(patch("/admin/cities/" + city.getCode()).with(adminHost())
				.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"live\":true}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("LIVE"));
		assertThat(cityRows.findById(city.getCode()).orElseThrow().isLive()).isTrue();
		assertThat(trail(ModerationTargetType.CITY, city.getCode())).containsExactly(ModerationActionType.ACTIVATE_CITY);
		// The test database is shared: other tests count the live seed cities.
		cityRows.deleteById(city.getCode());
	}

	@Test
	void partiesNonMembersAndTheMainSiteAreRefused() throws Exception {
		User admin = data.approved("Admin Tenant", Role.ADMIN, Role.TENANT);
		User landlord = data.approved("Conflict Landlord", Role.LANDLORD);
		Tenancy own = tenancy(landlord, admin);
		decide(admin, "tenancies/" + own.getPublicId() + "/approve", "{}")
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("admin_conflict"));
		assertThat(tenancies.findById(own.getId()).orElseThrow().getVerificationStatus()).isNull();

		Review written = review(tenancy(landlord, admin), ReviewDirection.TENANT_TO_LANDLORD);
		Report aboutOwn = new Report();
		aboutOwn.setReporter(landlord);
		aboutOwn.setTargetType(ReportTargetType.REVIEW);
		aboutOwn.setTargetId(written.getId());
		aboutOwn.setReason(ReportReason.HARASSMENT_OR_HATE);
		aboutOwn = reports.save(aboutOwn);
		decide(admin, "reports/" + aboutOwn.getPublicId() + "/resolve", "{\"outcome\":\"DISMISS\"}")
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("admin_conflict"));
		assertThat(reports.findById(aboutOwn.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.OPEN);

		// An admin token whose holder is not a member any more is refused at the decision.
		User former = admin();
		String stale = adminBearer(tokens, former);
		former.getRoles().clear();
		userRows.save(former);
		mvc.perform(post("/admin/properties/" + own.getProperty().getPublicId() + "/approve").with(adminHost())
				.header(HttpHeaders.AUTHORIZATION, stale).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());

		mvc.perform(get("/admin/queue").header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin())))
			.andExpect(status().isNotFound());
		mvc.perform(get("/admin/queue").with(adminHost())).andExpect(status().isUnauthorized());
		decide(admin(), "properties/" + UUID.randomUUID() + "/approve", "{}").andExpect(status().isNotFound());
		Property draft = data.property(landlord, "BLR", "Draft Lane", PropertyStatus.DRAFT);
		decide(admin(), "properties/" + draft.getPublicId() + "/approve", "{}").andExpect(status().isConflict());
	}

}
