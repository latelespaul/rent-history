package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.auth.google.FakeGoogleTokenVerifier;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.RecordingEmailSender;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.rentrix.rentrixserver.auth.AuthTestSupport.freshIp;
import static com.rentrix.rentrixserver.support.AdminConsole.adminBearer;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The welcome email, the admin review reminder and the unsubscribe link (D-172), against the recording sender. */
@IntegrationTest
class MailCampaignTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	UserRepository users;
	
	@Autowired
	ReviewRepository reviews;
	
	@Autowired
	MailSendRepository sends;
	
	@Autowired
	UnsubscribeTokens tokens;
	
	@Autowired
	AccessTokenService accessTokens;
	
	@Autowired
	RecordingEmailSender mail;
	
	/** Its own campaign per test: the suite shares one database, so other tests' users are recipients too. */
	private static String campaign() {
		return "review-reminder-" + UUID.randomUUID().toString().substring(0, 8);
	}
	
	private ResultActions remind(String body) throws Exception {
		User admin = data.user("Mail Admin", Role.ADMIN);
		return mvc.perform(post("/admin/mail/review-reminder").with(adminHost())
			.header("Authorization", adminBearer(accessTokens, admin))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}
	
	private ResultActions remind(String campaign, boolean dryRun, boolean onlyWithoutReview) throws Exception {
		return remind("""
			{"campaign":"%s","dryRun":%s,"onlyWithoutReview":%s}""".formatted(campaign, dryRun, onlyWithoutReview));
	}
	
	private User reviewer(String name) {
		User landlord = data.user(name + " Landlord", Role.LANDLORD);
		User tenant = data.verified(name, Role.TENANT);
		Property home = data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED);
		Tenancy tenancy = data.tenancy(home, tenant, LocalDate.of(2024, 1, 1), null, TenancyStatus.ACTIVE);
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(ReviewDirection.TENANT_TO_PROPERTY);
		review.setAuthor(tenant);
		review.setProperty(home);
		review.setRatingOverall(4);
		review.setBody("A calm, well kept home with a responsive owner overall.");
		review.setStatus(ReviewStatus.PUBLISHED);
		review.setSubmittedAt(Instant.parse("2025-02-01T00:00:00Z"));
		reviews.save(review);
		return tenant;
	}
	
	private static String token(EmailMessage message) {
		String url = message.unsubscribeUrl();
		return URLDecoder.decode(url.substring(url.indexOf("token=") + 6), StandardCharsets.UTF_8);
	}
	
	@Test
	void aGoogleSignUpGetsOneWelcomeWithTheReviewLinkAndAnUnsubscribeLink() throws Exception {
		String email = "welcome-" + UUID.randomUUID() + "@example.test";
		String idToken = FakeGoogleTokenVerifier.token("sub-" + UUID.randomUUID(), email, "Meera Nair");
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/auth/google").with(freshIp()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idToken\":\"" + idToken + "\"}")).andExpect(status().isOk());
		}
		List<EmailMessage> got = mail.to(email);
		assertThat(got).hasSize(1);
		EmailMessage welcome = got.getFirst();
		assertThat(welcome.subject()).isEqualTo("Welcome to Doorway Living");
		assertThat(welcome.body()).contains("Hi Meera Nair,", "Rate your landlord", "http://localhost:5173/homes/new",
			"Unsubscribe: http://localhost:5173/api/v1/mail/unsubscribe?token=");
		assertThat(welcome.html()).contains("Rate your landlord", "href=\"http://localhost:5173/homes/new\"");
		User user = users.findByEmail(email).orElseThrow();
		assertThat(sends.existsByUserAndCampaign(user, WelcomeMailer.CAMPAIGN)).isTrue();
		assertThat(tokens.verify(token(welcome))).contains(user.getPublicId());
	}
	
	@Test
	void theReminderSkipsReviewersOptedOutAndAlreadyMailedUsers() throws Exception {
		String campaign = campaign();
		User fresh = data.verified("Reminder Fresh", Role.TENANT);
		User reviewer = reviewer("Reminder Reviewer");
		User optedOut = data.verified("Reminder Opted Out", Role.TENANT);
		optedOut.setMailOptOutAt(Instant.parse("2026-01-01T00:00:00Z"));
		users.save(optedOut);
		User unverified = data.user("Reminder Unverified", Role.TENANT);
		
		String dry = remind(campaign, true, true).andExpect(status().isOk())
			.andExpect(jsonPath("$.dryRun").value(true))
			.andExpect(jsonPath("$.sent").value(0))
			.andReturn().getResponse().getContentAsString();
		assertThat(dry).doesNotContain("@");
		assertThat(mail.to(fresh.getEmail())).isEmpty();
		
		String sent = remind(campaign, false, true).andExpect(status().isOk())
			.andExpect(jsonPath("$.dryRun").value(false))
			.andExpect(jsonPath("$.failed").value(0))
			.andReturn().getResponse().getContentAsString();
		assertThat(sent).doesNotContain("@");
		EmailMessage reminder = mail.last(fresh.getEmail());
		assertThat(reminder.subject()).isEqualTo("Rate your landlord on Doorway Living");
		assertThat(reminder.body()).contains("http://localhost:5173/homes/new", "Unsubscribe:")
			.doesNotContain("Koramangala").doesNotContain(reviewer.getDisplayName());
		assertThat(reminder.unsubscribeUrl()).startsWith("http://localhost:5173/api/v1/mail/unsubscribe?token=");
		assertThat(mail.to(reviewer.getEmail())).isEmpty();
		assertThat(mail.to(optedOut.getEmail())).isEmpty();
		assertThat(mail.to(unverified.getEmail())).isEmpty();
		
		// A second run of the same campaign mails nobody twice.
		remind(campaign, false, true).andExpect(status().isOk()).andExpect(jsonPath("$.recipients").value(0));
		assertThat(mail.to(fresh.getEmail())).hasSize(1);
		
		// Without the filter, the reviewer gets it too, once.
		remind(campaign, false, false).andExpect(status().isOk()).andExpect(jsonPath("$.failed").value(0));
		assertThat(mail.to(reviewer.getEmail())).hasSize(1);
		assertThat(mail.to(fresh.getEmail())).hasSize(1);
	}
	
	@Test
	void aFailedEmailIsNotRecordedSoTheNextRunRetriesIt() throws Exception {
		String campaign = campaign();
		User bouncing = data.verified("Reminder Bounce", Role.TENANT);
		mail.failFor(bouncing.getEmail());
		try {
			remind(campaign, false, true).andExpect(status().isOk())
				.andExpect(jsonPath("$.failed").value(1));
		} finally {
			mail.stopFailing(bouncing.getEmail());
		}
		assertThat(sends.existsByUserAndCampaign(bouncing, campaign)).isFalse();
		remind(campaign, true, true).andExpect(status().isOk()).andExpect(jsonPath("$.recipients").value(1));
		remind(campaign, false, true).andExpect(status().isOk()).andExpect(jsonPath("$.sent").value(1));
		assertThat(mail.to(bouncing.getEmail())).hasSize(1);
	}
	
	@Test
	void theDefaultsAreADryRunOfTheFilteredReminder() throws Exception {
		remind("{}").andExpect(status().isOk())
			.andExpect(jsonPath("$.dryRun").value(true))
			.andExpect(jsonPath("$.mailEnabled").value(true));
		remind("{\"campaign\":\"welcome\"}").andExpect(status().isUnprocessableContent());
	}
	
	@Test
	void onlyAnAdminConsoleSessionOnTheAdminHostMaySendIt() throws Exception {
		User tenant = data.verified("Reminder Tenant", Role.TENANT);
		String mainSite = "Bearer " + accessTokens.issue(tenant.getPublicId(), List.of(Role.TENANT));
		mvc.perform(post("/admin/mail/review-reminder").with(adminHost()).header("Authorization", mainSite)
			.contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":false}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/admin/mail/review-reminder").with(adminHost())
			.header("Authorization", adminBearer(accessTokens, tenant))
			.contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":false}"))
			.andExpect(status().isForbidden());
		User admin = data.user("Main Host Admin", Role.ADMIN);
		mvc.perform(post("/admin/mail/review-reminder").header("Authorization", adminBearer(accessTokens, admin))
			.contentType(MediaType.APPLICATION_JSON).content("{\"dryRun\":false}"))
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 404));
		mvc.perform(post("/admin/mail/review-reminder").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 404));
		assertThat(mail.to(tenant.getEmail())).isEmpty();
	}
	
	@Test
	void theUnsubscribeLinkOptsTheUserOutAndTheReminderSkipsThem() throws Exception {
		User user = data.verified("Unsubscribe Me", Role.TENANT);
		mvc.perform(get("/mail/unsubscribe").param("token", tokens.issue(user.getPublicId())))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
			.andExpect(content().string(containsString("You are unsubscribed")));
		assertThat(users.findById(user.getId()).orElseThrow().getMailOptOutAt()).isNotNull();
		remind(campaign(), false, true).andExpect(status().isOk());
		assertThat(mail.to(user.getEmail())).isEmpty();
	}
	
	@Test
	void theMailAppsOneClickUnsubscribeWorksToo() throws Exception {
		User user = data.verified("One Click", Role.TENANT);
		mvc.perform(post("/mail/unsubscribe").param("token", tokens.issue(user.getPublicId()))
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).content("List-Unsubscribe=One-Click"))
			.andExpect(status().isOk());
		assertThat(users.findById(user.getId()).orElseThrow().getMailOptOutAt()).isNotNull();
	}
	
	@Test
	void aForgedTokenChangesNothing() throws Exception {
		User target = data.verified("Forge Target", Role.TENANT);
		User attacker = data.verified("Forge Attacker", Role.TENANT);
		String own = tokens.issue(attacker.getPublicId());
		String forged = target.getPublicId() + own.substring(own.indexOf('.'));
		mvc.perform(get("/mail/unsubscribe").param("token", forged))
			.andExpect(status().isBadRequest())
			.andExpect(content().string(containsString("This link does not work")));
		mvc.perform(get("/mail/unsubscribe")).andExpect(status().isBadRequest());
		assertThat(users.findById(target.getId()).orElseThrow().getMailOptOutAt()).isNull();
	}
	
}
