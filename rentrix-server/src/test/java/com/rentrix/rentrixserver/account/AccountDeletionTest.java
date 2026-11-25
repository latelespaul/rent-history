package com.rentrix.rentrixserver.account;

import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.rentrix.rentrixserver.auth.AuthTestSupport.freshIp;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshCookie;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshToken;
import static com.rentrix.rentrixserver.support.AdminConsole.adminBearer;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@code DELETE /me} (D-58, D-157 – D-160, rentrix-docs/design/delete-account.md): personal data is erased, other
 * people's records stay as "Deleted user", every session ends at once, the admin audit log shows the deletion with the
 * user id and time only, and signing up again with the same Google account (email) and phone starts fresh. Same
 * context as {@code DevLoginTest} (dev login on), so the sign-ins are real sessions with refresh cookies.
 */
@SpringBootTest(properties = {"app.dev-login.enabled=true", "app.admin-console.emails=console-admin@example.test,listed-demo@example.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AccountDeletionTest {

	private static final Pattern SMS_LINE =
		Pattern.compile("SMS to \\+91 XXXXXX(\\d{4}) \\(SMS_BACKEND=log, not sent\\): (\\d{6}) is your Doorway Living");
	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
	private static final String REVIEW =
		"Bright rooms, steady water and power all year, and a quiet lane. Repairs were done within a week.";

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper json;

	@Autowired
	TestData data;

	@Autowired
	UserRepository users;

	@Autowired
	PropertyRepository properties;

	@Autowired
	TenancyRepository tenancies;

	@Autowired
	UnclaimedLandlord unclaimed;

	@Autowired
	AccessTokenService tokens;

	@Autowired
	JdbcTemplate jdbc;

	/** A signed-in session: the access token, the raw refresh token and the user's public id. */
	private record Session(String accessToken, String refreshToken, UUID userId) {

		String bearer() {
			return "Bearer " + accessToken;
		}

	}

	private static String key() {
		return Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36);
	}

	/** A random Indian mobile outside the test range, so the code goes to the log sender. */
	private static String phone() {
		return "+9198" + String.format("%08d", ThreadLocalRandom.current().nextLong(100_000_000L));
	}

	private Session session(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		JsonNode body = json.readTree(response.getContentAsString());
		return new Session(body.get("accessToken").asString(), refreshToken(response),
			UUID.fromString(body.at("/user/id").asString()));
	}

	/** {@code POST /auth/dev-login/new} for the Google account {@code key} stands for. */
	private Session signUp(String key) throws Exception {
		return session(mvc.perform(post("/auth/dev-login/new").with(freshIp()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"key\":\"" + key + "\"}")).andReturn().getResponse());
	}

	private Session signInAgain(UUID userId) throws Exception {
		return session(mvc.perform(post("/auth/dev-login").with(freshIp()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"" + userId + "\"}")).andReturn().getResponse());
	}

	private String onboardAsTenant(Session session) throws Exception {
		MockHttpServletResponse response = mvc.perform(post("/me/onboarding")
				.header(HttpHeaders.AUTHORIZATION, session.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":[\"TENANT\"],\"acceptTerms\":true}"))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(200);
		return "Bearer " + json.readTree(response.getContentAsString()).get("accessToken").asString();
	}

	/** Verifies {@code phone} through the real code flow, reading the code from the log line. */
	private JsonNode verifyPhone(String bearer, String phone, CapturedOutput output) throws Exception {
		MockHttpServletResponse started = mvc.perform(post("/me/identity/phone/start")
				.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\",\"channel\":\"SMS\"}"))
			.andReturn().getResponse();
		assertThat(started.getStatus()).as(started.getContentAsString()).isBetween(200, 201);
		String challengeId = json.readTree(started.getContentAsString()).get("challengeId").asString();
		String code = null;
		Matcher m = SMS_LINE.matcher(output.getOut());
		while (m.find()) {
			if (phone.endsWith(m.group(1))) {
				code = m.group(2);
			}
		}
		assertThat(code).as("the logged code").isNotNull();
		MockHttpServletResponse confirmed = mvc.perform(post("/me/identity/phone/confirm")
				.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + code + "\"}"))
			.andReturn().getResponse();
		assertThat(confirmed.getStatus()).as(confirmed.getContentAsString()).isEqualTo(200);
		return json.readTree(confirmed.getContentAsString());
	}

	private MockHttpServletResponse deleteMe(String bearer, String confirm) throws Exception {
		return mvc.perform(delete("/me").header(HttpHeaders.AUTHORIZATION, bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(confirm == null ? "{}" : "{\"confirm\":\"" + confirm + "\"}")).andReturn().getResponse();
	}

	private String code(MockHttpServletResponse response) throws Exception {
		return json.readTree(response.getContentAsString()).get("code").asString();
	}

	private int count(String sql, Object... args) {
		Integer n = jdbc.queryForObject(sql, Integer.class, args);
		return n == null ? 0 : n;
	}

	@Test
	void deletingErasesPersonalDataKeepsRecordsAsDeletedUserAndASecondSignUpStartsFresh(CapturedOutput output)
		throws Exception {
		String key = key();
		String phone = phone();
		Session first = signUp(key);
		String bearer = onboardAsTenant(first);
		verifyPhone(bearer, phone, output);
		User me = users.findByPublicId(first.userId()).orElseThrow();
		assertThat(me.getPhone()).isEqualTo(phone);
		data.approve(me); // verification applications, so their erasure is checked too
		Instant aged = Instant.now().minus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MILLIS);
		jdbc.update("update users set created_at = ? where id = ?", Timestamp.from(aged), me.getId());
		Session second = signInAgain(first.userId()); // another device

		// Other people's records the user is part of.
		User landlord = data.approved("Kept Landlord", Role.LANDLORD);
		Property rented = data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED);
		Tenancy tenancy = data.tenancy(rented, me, TODAY.minusYears(1), null, TenancyStatus.ACTIVE);
		MockHttpServletResponse reviewed = mvc.perform(post("/tenancies/" + tenancy.getPublicId() + "/reviews")
				.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"direction\":\"TENANT_TO_PROPERTY\",\"ratingOverall\":4,\"body\":\"" + REVIEW + "\"}"))
			.andReturn().getResponse();
		assertThat(reviewed.getStatus()).as(reviewed.getContentAsString()).isEqualTo(201);
		Property otherHome = data.property(landlord, "BLR", "Indiranagar", PropertyStatus.PUBLISHED);
		Tenancy pending = data.tenancy(otherHome, me, TODAY.minusMonths(2), null, TenancyStatus.PENDING_CONFIRMATION);
		pending.setInviteEmail(me.getEmail());
		tenancies.save(pending);
		// A home the user owns, and one they added as a tenant with the owner's contact.
		Property owned = data.property(me, "PNQ", "Baner", PropertyStatus.PUBLISHED);
		Property added = data.property(unclaimed.get(), "PNQ", "Aundh", PropertyStatus.PUBLISHED);
		added.setAddedBy(me);
		added.setOwnerName("Suresh Owner");
		added.setOwnerEmail("suresh@example.test");
		added.setOwnerPhone("+919812345678");
		properties.save(added);

		// Re-confirm: the typed word, then a recent sign-in.
		MockHttpServletResponse mismatch = deleteMe(bearer, "delete");
		assertThat(mismatch.getStatus()).isEqualTo(422);
		assertThat(code(mismatch)).isEqualTo("confirm_mismatch");
		String stale = "Bearer " + tokens.issue(me.getPublicId(), List.of(Role.TENANT),
			Instant.now().minus(Duration.ofMinutes(11)));
		MockHttpServletResponse reauth = deleteMe(stale, "DELETE");
		assertThat(reauth.getStatus()).isEqualTo(403);
		assertThat(code(reauth)).isEqualTo("reauth_required");
		assertThat(users.findByPublicId(first.userId()).orElseThrow().getDeletedAt()).isNull();

		MockHttpServletResponse deleted = deleteMe(bearer, "DELETE");
		assertThat(deleted.getStatus()).as(deleted.getContentAsString()).isEqualTo(204);
		assertThat(deleted.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

		// Every session ends at once: live access tokens and both devices' refresh tokens.
		for (String old : List.of(bearer, second.bearer())) {
			MockHttpServletResponse me2 = mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, old))
				.andReturn().getResponse();
			assertThat(me2.getStatus()).isEqualTo(401);
			assertThat(code(me2)).isEqualTo("token_invalid");
		}
		for (String refresh : List.of(first.refreshToken(), second.refreshToken())) {
			assertThat(mvc.perform(post("/auth/refresh").cookie(refreshCookie(refresh))).andReturn().getResponse()
				.getStatus()).isEqualTo(401);
		}

		// Erased.
		Map<String, Object> row = jdbc.queryForMap("select * from users where public_id = ?",
			first.userId().toString());
		assertThat(row.get("email")).isNull();
		assertThat(row.get("google_sub")).isNull();
		assertThat(row.get("google_name")).isNull();
		assertThat(row.get("avatar_url")).isNull();
		assertThat(row.get("phone")).isNull();
		assertThat(row.get("display_name")).isEqualTo("Deleted user");
		assertThat(row.get("status")).isEqualTo("DELETED");
		assertThat(row.get("deleted_at")).isNotNull();
		assertThat(count("select count(*) from user_roles where user_id = ?", me.getId())).isZero();
		assertThat(count("select count(*) from identity_verifications where user_id = ?", me.getId())).isZero();
		assertThat(count("select count(*) from verification_applications where user_id = ?", me.getId())).isZero();
		assertThat(count("select count(*) from refresh_tokens where user_id = ?", me.getId())).isZero();
		assertThat(count("select count(*) from phone_otp_challenges where user_id = ? and phone_e164 <> 'erased'",
			me.getId())).as("no phone number is kept").isZero();
		assertThat(count("select count(*) from phone_otp_challenges where user_id = ? and consumed_at is null",
			me.getId())).as("every code is used up").isZero();
		assertThat(output.getOut()).doesNotContain(phone);

		// Kept, anonymised.
		assertThat(jdbc.queryForObject("select landlord_id from properties where id = ?", Long.class, owned.getId()))
			.as("owner detached").isEqualTo(unclaimed.get().getId());
		Property addedNow = properties.findById(added.getId()).orElseThrow();
		assertThat(addedNow.getOwnerName()).isNull();
		assertThat(addedNow.getOwnerEmail()).isNull();
		assertThat(addedNow.getOwnerPhone()).isNull();
		assertThat(tenancies.findById(tenancy.getId()).orElseThrow().getStatus()).isEqualTo(TenancyStatus.ACTIVE);
		Tenancy pendingNow = tenancies.findById(pending.getId()).orElseThrow();
		assertThat(pendingNow.getStatus()).isEqualTo(TenancyStatus.CANCELLED);
		assertThat(pendingNow.getInviteEmail()).isNull();
		String landlordBearer = "Bearer " + tokens.issue(landlord.getPublicId(), landlord.getRoles());
		JsonNode kept = json.readTree(mvc.perform(get("/tenancies/" + tenancy.getPublicId())
			.header(HttpHeaders.AUTHORIZATION, landlordBearer)).andReturn().getResponse().getContentAsString());
		assertThat(kept.at("/tenant/shortName").asString()).isEqualTo("Deleted user");
		assertThat(kept.at("/tenant/id").isMissingNode() || kept.at("/tenant/id").isNull()).isTrue();
		JsonNode reviews = json.readTree(mvc.perform(get("/properties/" + rented.getPublicId() + "/reviews")
			.header(HttpHeaders.AUTHORIZATION, landlordBearer)).andReturn().getResponse().getContentAsString());
		assertThat(reviews.at("/items/0/body").asString()).isEqualTo(REVIEW);
		assertThat(reviews.at("/items/0/author/label").asString()).isEqualTo("Deleted user");

		// The audit log: the user id and time only, shown to admins.
		Map<String, Object> audit = jdbc.queryForMap(
			"select * from moderation_actions where action = 'ACCOUNT_DELETED' and target_ref = ?",
			first.userId().toString());
		assertThat(audit.get("reason")).isNull();
		assertThat(audit.get("target_type")).isEqualTo("USER");
		User admin = data.user("Audit Admin " + key(), Role.ADMIN);
		JsonNode log = json.readTree(mvc.perform(get("/admin/audit-log").with(adminHost())
				.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)).param("action", "ACCOUNT_DELETED"))
			.andReturn().getResponse().getContentAsString());
		JsonNode entry = null;
		for (JsonNode item : log.get("items")) {
			if (first.userId().toString().equals(item.get("targetRef").asString())) {
				entry = item;
			}
		}
		assertThat(entry).as("the deletion is in the audit log").isNotNull();
		assertThat(entry.get("self").asBoolean()).isTrue();
		assertThat(entry.get("actor").isNull()).isTrue();
		assertThat(entry.get("createdAt").asString()).isNotBlank();

		// Signing up again with the same Google account (email) and phone starts fresh.
		Session again = signUp(key);
		assertThat(again.userId()).isNotEqualTo(first.userId());
		JsonNode fresh = json.readTree(mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, again.bearer()))
			.andReturn().getResponse().getContentAsString());
		assertThat(fresh.get("email").asString()).isEqualTo("new-user-" + key + "@example.test");
		assertThat(fresh.get("needsOnboarding").asBoolean()).isTrue();
		String againBearer = onboardAsTenant(again);
		JsonNode identity = verifyPhone(againBearer, phone, output);
		assertThat(identity.toString()).contains("VERIFIED");
		User newMe = users.findByPublicId(again.userId()).orElseThrow();
		assertThat(newMe.getPhone()).isEqualTo(phone);
		assertThat(count("select count(*) from tenancies where tenant_id = ?", newMe.getId())).isZero();
		assertThat(count("select count(*) from reviews where author_id = ?", newMe.getId())).isZero();
	}

	@Test
	void anUnconfirmedOrStaleRequestChangesNothingAndASecondDeleteIsRefused() throws Exception {
		Session session = signUp(key());
		MockHttpServletResponse none = deleteMe(session.bearer(), null);
		assertThat(none.getStatus()).isEqualTo(422);
		assertThat(code(none)).isEqualTo("confirm_mismatch");
		// A token without auth_time (issued before D-157) counts as an old sign-in.
		String legacy = "Bearer " + tokens.issue(session.userId(), List.of(), null);
		assertThat(code(deleteMe(legacy, "DELETE"))).isEqualTo("reauth_required");
		assertThat(deleteMe(session.bearer(), "DELETE").getStatus()).isEqualTo(204);
		assertThat(deleteMe(session.bearer(), "DELETE").getStatus()).as("the old token is refused").isEqualTo(401);
	}

}
