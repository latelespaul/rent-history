package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phone OTP (D-101) on the test profile, where {@code SMS_BACKEND=log} writes the SMS to the log: each test reads
 * its code from that one line and checks that nothing else ever logs or returns the code or the full number.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PhoneOtpTest {
	
	private static final Pattern SMS_LINE = Pattern.compile("SMS to (\\+91 XXXXXX\\d{4}) \\(SMS_BACKEND=log, not sent\\): (\\d{6}) is your Rentrix");
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	ObjectMapper json;
	
	@Autowired
	TestData data;
	
	@Autowired
	UserRepository users;
	
	@Autowired
	AccessTokenService tokens;
	
	@Autowired
	IdentityService identity;
	
	@Autowired
	IdentityVerificationRepository checks;
	
	@Autowired
	PhoneOtpChallengeRepository challenges;
	
	@Autowired
	PhoneOtpCleanupJob cleanup;
	
	/** A random Indian mobile number, so tests never share a per-number limit. */
	static String phone() {
		return "+919" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
	}
	
	private String bearer(User user) {
		return "Bearer " + tokens.issue(user.getPublicId(), user.getRoles());
	}
	
	private MockHttpServletResponse start(User user, String phone) throws Exception {
		return mvc.perform(post("/me/identity/phone/start").header(HttpHeaders.AUTHORIZATION, bearer(user))
			.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}")).andReturn().getResponse();
	}
	
	private MockHttpServletResponse confirm(User user, String challengeId, String code) throws Exception {
		return mvc.perform(post("/me/identity/phone/confirm").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + code + "\"}"))
			.andReturn().getResponse();
	}
	
	private JsonNode body(MockHttpServletResponse response) throws Exception {
		return json.readTree(response.getContentAsString());
	}
	
	/** The code in the last log line of the SMS sent to {@code phone}. */
	private static String codeSentTo(CapturedOutput output, String phone) {
		String masked = "+91 XXXXXX" + phone.substring(phone.length() - 4);
		String code = null;
		for (String line : output.getOut().lines().toList()) {
			Matcher m = SMS_LINE.matcher(line);
			if (m.find() && m.group(1).equals(masked)) {
				code = m.group(2);
			}
		}
		assertThat(code).as("SMS to %s in the log", masked).isNotNull();
		return code;
	}
	
	/** Verifies {@code phone} for {@code user} through the API and returns the confirm response body. */
	private JsonNode verify(User user, String phone, CapturedOutput output) throws Exception {
		MockHttpServletResponse started = start(user, phone);
		assertThat(started.getStatus()).as(started.getContentAsString()).isEqualTo(200);
		String challengeId = body(started).get("challengeId").asString();
		MockHttpServletResponse confirmed = confirm(user, challengeId, codeSentTo(output, phone));
		assertThat(confirmed.getStatus()).as(confirmed.getContentAsString()).isEqualTo(200);
		return body(confirmed);
	}
	
	private static JsonNode method(JsonNode view, String method) {
		for (JsonNode m : view.get("methods")) {
			if (m.get("method").asString().equals(method)) {
				return m;
			}
		}
		throw new AssertionError("no " + method + " in " + view);
	}
	
	@Test
	void happyPathVerifiesThePhoneAndNeverLeaksTheCodeOrNumber(CapturedOutput output) throws Exception {
		User user = data.user("Asha", Role.TENANT);
		String phone = phone();
		String last4 = phone.substring(phone.length() - 4);
		
		MockHttpServletResponse started = start(user, phone);
		assertThat(started.getStatus()).isEqualTo(200);
		JsonNode startBody = body(started);
		String challengeId = startBody.get("challengeId").asString();
		Instant expiresAt = Instant.parse(startBody.get("expiresAt").asString());
		assertThat(expiresAt).isBetween(Instant.now().plus(9, ChronoUnit.MINUTES), Instant.now().plus(11, ChronoUnit.MINUTES));
		String code = codeSentTo(output, phone);
		
		MockHttpServletResponse confirmed = confirm(user, challengeId, code);
		assertThat(confirmed.getStatus()).as(confirmed.getContentAsString()).isEqualTo(200);
		JsonNode view = body(confirmed);
		assertThat(view.get("idVerified").asBoolean()).isTrue();
		assertThat(view.get("linkedinVerified").asBoolean()).isFalse();
		JsonNode phoneMethod = method(view, "PHONE_OTP");
		assertThat(phoneMethod.get("status").asString()).isEqualTo("VERIFIED");
		assertThat(phoneMethod.get("masked").asString()).isEqualTo("+91 XXXXXX" + last4);
		assertThat(phoneMethod.get("checkedBy").asString()).isEqualTo("OTP");
		assertThat(phoneMethod.get("available").asBoolean()).isTrue();
		
		User saved = users.findById(user.getId()).orElseThrow();
		assertThat(saved.getPhone()).isEqualTo(phone);
		IdentityVerification row = checks.findByUserAndMethod(saved, IdentityMethod.PHONE_OTP).orElseThrow();
		assertThat(row.getLast4()).isEqualTo(last4);
		assertThat(row.getValueHmac()).hasSize(64).doesNotContain(phone).doesNotContain(phone.substring(1));
		PhoneOtpChallenge challenge = challenges.findByPublicIdAndUser(UUID.fromString(challengeId), saved).orElseThrow();
		assertThat(challenge.getCodeHmac()).hasSize(64).isNotEqualTo(code);
		assertThat(challenge.getConsumedAt()).isNotNull();
		
		MockHttpServletResponse me = mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.idVerified").value(true))
			.andExpect(jsonPath("$.badges.linkedinVerified").value(false))
			.andExpect(jsonPath("$.phone").value("+91 XXXXXX" + last4))
			.andReturn().getResponse();
		MockHttpServletResponse identityView = mvc.perform(get("/me/identity")
				.header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk()).andReturn().getResponse();
		
		// The full number and the code never reach a response; the code is only in the LogSmsSender line.
		String national = phone.substring(3);
		for (MockHttpServletResponse response : List.of(started, confirmed, me, identityView)) {
			assertThat(response.getContentAsString()).doesNotContain(national).doesNotContain(code);
		}
		assertThat(output.getAll()).doesNotContain(national);
		assertThat(output.getAll().lines().filter(l -> l.contains(code)))
			.allMatch(l -> SMS_LINE.matcher(l).find());
		assertThat(identity.isIdVerified(saved)).isTrue();
	}
	
	@Test
	void wrongCodeUsesUpAttemptsAndTheSixthTryIsRefused(CapturedOutput output) throws Exception {
		User user = data.user("Ravi", Role.LANDLORD);
		String phone = phone();
		String challengeId = body(start(user, phone)).get("challengeId").asString();
		String code = codeSentTo(output, phone);
		String wrong = code.equals("000000") ? "111111" : "000000";
		for (int left = 4; left >= 0; left--) {
			MockHttpServletResponse response = confirm(user, challengeId, wrong);
			assertThat(response.getStatus()).isEqualTo(422);
			JsonNode problem = body(response);
			assertThat(problem.get("code").asString()).isEqualTo("otp_invalid");
			assertThat(problem.get("detail").asString()).contains(left + (left == 1 ? " try" : " tries") + " left");
		}
		// Sixth try: refused even with the right code.
		MockHttpServletResponse sixth = confirm(user, challengeId, code);
		assertThat(sixth.getStatus()).isEqualTo(422);
		assertThat(body(sixth).get("code").asString()).isEqualTo("otp_attempts_exceeded");
		assertThat(identity.isIdVerified(user)).isFalse();
		assertThat(challenges.findByPublicIdAndUser(UUID.fromString(challengeId), user).orElseThrow().getAttempts())
			.isEqualTo(5);
	}
	
	@Test
	void expiredCodeIsRefused(CapturedOutput output) throws Exception {
		User user = data.user("Meera", Role.TENANT);
		String phone = phone();
		String challengeId = body(start(user, phone)).get("challengeId").asString();
		String code = codeSentTo(output, phone);
		PhoneOtpChallenge challenge = challenges.findByPublicIdAndUser(UUID.fromString(challengeId), user).orElseThrow();
		challenge.setExpiresAt(Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS));
		challenges.save(challenge);
		MockHttpServletResponse response = confirm(user, challengeId, code);
		assertThat(response.getStatus()).isEqualTo(422);
		assertThat(body(response).get("code").asString()).isEqualTo("otp_expired");
		assertThat(identity.isIdVerified(user)).isFalse();
	}
	
	@Test
	void aUsedCodeCannotBeUsedAgain(CapturedOutput output) throws Exception {
		User user = data.user("Kiran", Role.TENANT);
		String phone = phone();
		String challengeId = body(start(user, phone)).get("challengeId").asString();
		String code = codeSentTo(output, phone);
		assertThat(confirm(user, challengeId, code).getStatus()).isEqualTo(200);
		MockHttpServletResponse again = confirm(user, challengeId, code);
		assertThat(again.getStatus()).isEqualTo(422);
		assertThat(body(again).get("code").asString()).isEqualTo("otp_expired");
	}
	
	@Test
	void fourthCodeInAnHourIs429WithRetryAfter() throws Exception {
		User user = data.user("Neha", Role.TENANT);
		for (int i = 0; i < 3; i++) {
			assertThat(start(user, phone()).getStatus()).isEqualTo(200);
		}
		mvc.perform(post("/me/identity/phone/start").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone() + "\"}"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("rate_limited"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}
	
	@Test
	void tenCodesPerUserPerDay() throws Exception {
		User user = data.user("Dev", Role.TENANT);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		for (int i = 0; i < 10; i++) {
			PhoneOtpChallenge old = new PhoneOtpChallenge();
			old.setUser(user);
			old.setPhoneE164(phone());
			old.setPhoneHmac("a".repeat(64));
			old.setCodeHmac("b".repeat(64));
			old.setCreatedAt(now.minus(2 + i, ChronoUnit.HOURS));
			old.setExpiresAt(old.getCreatedAt().plus(10, ChronoUnit.MINUTES));
			challenges.save(old);
		}
		MockHttpServletResponse response = start(user, phone());
		assertThat(response.getStatus()).isEqualTo(429);
		// The oldest of the ten (11 h ago) leaves the 24 h window in about 13 h.
		long retryAfter = Long.parseLong(response.getHeader(HttpHeaders.RETRY_AFTER));
		assertThat(retryAfter).isBetween(12 * 3600L, 13 * 3600L + 60);
	}
	
	@Test
	void fiveCodesPerNumberPerDayAcrossAccounts() throws Exception {
		String shared = phone();
		for (int i = 0; i < 5; i++) {
			assertThat(start(data.user("Family " + i, Role.TENANT), shared).getStatus()).isEqualTo(200);
		}
		assertThat(start(data.user("Family 6", Role.TENANT), shared).getStatus()).isEqualTo(429);
	}
	
	@Test
	void samePhoneOnTwoAccountsFlagsBoth(CapturedOutput output) throws Exception {
		String shared = phone();
		User first = data.user("Parent", Role.LANDLORD);
		User second = data.user("Child", Role.TENANT);
		verify(first, shared, output);
		assertThat(identity.flags(first)).isEmpty();
		verify(second, shared, output);
		// Shared family phones exist, so nothing is blocked: both stay verified, both are flagged for the admin.
		assertThat(identity.isIdVerified(first)).isTrue();
		assertThat(identity.isIdVerified(second)).isTrue();
		assertThat(identity.flags(first)).containsExactly(IdentityService.DUPLICATE_PHONE);
		assertThat(identity.flags(second)).containsExactly(IdentityService.DUPLICATE_PHONE);
		assertThat(identity.flags(data.user("Stranger", Role.TENANT))).isEmpty();
	}
	
	@Test
	void changingThePhoneRevokesTheCheck(CapturedOutput output) throws Exception {
		User user = data.user("Sana", Role.TENANT);
		String phone = phone();
		verify(user, phone, output);
		// Saving the same number again changes nothing.
		mvc.perform(patch("/me").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.badges.idVerified").value(true));
		mvc.perform(patch("/me").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone() + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.badges.idVerified").value(false));
		JsonNode view = body(mvc.perform(get("/me/identity").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk()).andReturn().getResponse());
		JsonNode phoneMethod = method(view, "PHONE_OTP");
		assertThat(phoneMethod.get("status").asString()).isEqualTo("REVOKED");
		assertThat(phoneMethod.get("reject").get("code").asString()).isEqualTo("PHONE_CHANGED");
		assertThat(view.get("idVerified").asBoolean()).isFalse();
	}
	
	@Test
	void alreadyVerifiedNumberIs409AndOtherInputErrorsAre422(CapturedOutput output) throws Exception {
		User user = data.user("Tara", Role.TENANT);
		String phone = phone();
		verify(user, phone, output);
		MockHttpServletResponse again = start(user, phone);
		assertThat(again.getStatus()).isEqualTo(409);
		assertThat(body(again).get("code").asString()).isEqualTo("phone_already_verified");
		
		for (String bad : List.of("+14155550123", "5876543210", "+91987654321", "phone")) {
			MockHttpServletResponse response = start(user, bad);
			assertThat(response.getStatus()).as(bad).isEqualTo(422);
			assertThat(body(response).get("errors").get(0).get("field").asString()).isEqualTo("phone");
		}
		String challengeId = body(start(user, phone())).get("challengeId").asString();
		assertThat(confirm(user, challengeId, "12ab56").getStatus()).isEqualTo(422);
	}
	
	@Test
	void anotherUsersChallengeIs404(CapturedOutput output) throws Exception {
		User owner = data.user("Owner", Role.TENANT);
		User other = data.user("Other", Role.TENANT);
		String phone = phone();
		String challengeId = body(start(owner, phone)).get("challengeId").asString();
		MockHttpServletResponse response = confirm(other, challengeId, codeSentTo(output, phone));
		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(identity.isIdVerified(other)).isFalse();
	}
	
	@Test
	void cleanupDeletesChallengesADayAfterTheyFinish() {
		User user = data.user("Old", Role.TENANT);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		PhoneOtpChallenge stale = new PhoneOtpChallenge();
		stale.setUser(user);
		stale.setPhoneE164(phone());
		stale.setPhoneHmac("c".repeat(64));
		stale.setCodeHmac("d".repeat(64));
		stale.setCreatedAt(now.minus(26, ChronoUnit.HOURS));
		stale.setExpiresAt(now.minus(25, ChronoUnit.HOURS));
		challenges.save(stale);
		PhoneOtpChallenge recent = new PhoneOtpChallenge();
		recent.setUser(user);
		recent.setPhoneE164(phone());
		recent.setPhoneHmac("e".repeat(64));
		recent.setCodeHmac("f".repeat(64));
		recent.setCreatedAt(now.minus(2, ChronoUnit.HOURS));
		recent.setExpiresAt(now.minus(1, ChronoUnit.HOURS));
		challenges.save(recent);
		assertThat(cleanup.purge()).isGreaterThanOrEqualTo(1);
		assertThat(challenges.findById(stale.getId())).isEmpty();
		assertThat(challenges.findById(recent.getId())).isPresent();
	}
	
	@Test
	void identityRoutesNeedOnboardingFirst() throws Exception {
		User fresh = data.user("New");
		mvc.perform(get("/me/identity")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(fresh.getPublicId(), Set.of())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("onboarding_required"));
		mvc.perform(get("/me/identity")).andExpect(status().isUnauthorized());
	}
	
}
