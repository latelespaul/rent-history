package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.auth.google.FakeGoogleTokenVerifier;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.freshIp;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.ip;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshCookie;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Google sign-in, refresh rotation, logout, onboarding and the rate limit (§13, PLAN B3). */
@IntegrationTest
class AuthFlowTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	ObjectMapper json;
	
	@Autowired
	UserRepository users;
	
	@Autowired
	TestData data;
	
	@Autowired
	AccessTokenService tokens;
	
	@Autowired
	RefreshTokenRepository refreshTokens;
	
	@Autowired
	TransactionTemplate transactions;
	
	private static String sub() {
		return "sub-" + UUID.randomUUID();
	}
	
	private static String email() {
		return "u-" + UUID.randomUUID() + "@example.test";
	}
	
	private MockHttpServletResponse google(String idToken, RequestPostProcessor ip) throws Exception {
		return mvc.perform(post("/auth/google").with(ip).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(java.util.Map.of("idToken", idToken))))
			.andReturn().getResponse();
	}
	
	private JsonNode body(MockHttpServletResponse response) throws Exception {
		return json.readTree(response.getContentAsString());
	}
	
	private JsonNode signIn(String sub, String email) throws Exception {
		MockHttpServletResponse response = google(FakeGoogleTokenVerifier.token(sub, email, "Asha Rao"), freshIp());
		assertThat(response.getStatus()).isEqualTo(200);
		return body(response);
	}
	
	private static String bearer(JsonNode auth) {
		return "Bearer " + auth.get("accessToken").asString();
	}
	
	private JsonNode onboard(JsonNode auth, String rolesJson) throws Exception {
		MockHttpServletResponse response = mvc.perform(post("/me/onboarding")
				.header(HttpHeaders.AUTHORIZATION, bearer(auth)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":" + rolesJson + ",\"acceptTerms\":true}"))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(200);
		return body(response);
	}
	
	@Test
	void configShowsGoogleClientIdAndNoDevLoginInTestProfile() throws Exception {
		mvc.perform(get("/auth/config"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.googleClientId").value("fake-test-client-id"))
			.andExpect(jsonPath("$.devLogin").value(false));
	}
	
	@Test
	void devLoginRoutesAre404WhenNotEnabled() throws Exception {
		mvc.perform(get("/auth/dev-users")).andExpect(status().isNotFound());
		mvc.perform(post("/auth/dev-login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isNotFound());
	}
	
	@Test
	void newGoogleUserIsCreatedWithoutRolesAndNeedsOnboarding() throws Exception {
		String sub = sub();
		String email = email();
		MockHttpServletResponse response = google(FakeGoogleTokenVerifier.token(sub, email.toUpperCase(), "Asha Rao"),
			freshIp());
		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode auth = body(response);
		assertThat(auth.get("needsOnboarding").asBoolean()).isTrue();
		assertThat(auth.at("/user/roles")).isEmpty();
		assertThat(auth.at("/user/displayName").asString()).isEqualTo("Asha Rao");
		assertThat(auth.at("/user/email").asString()).isEqualTo(email);
		assertThat(auth.at("/user/emailVerified").asBoolean()).isTrue();
		assertThat(auth.at("/user/verification")).isEmpty();
		assertThat(tokens.verify(auth.get("accessToken").asString()).roles()).isEmpty();
		
		String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(cookie).contains(RefreshCookies.NAME + "=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/auth",
			"Max-Age=2592000");
		assertThat(response.getContentAsString()).doesNotContain(refreshToken(response));
		
		User stored = users.findByGoogleSub(sub).orElseThrow();
		assertThat(stored.getEmail()).isEqualTo(email);
		assertThat(stored.getEmailVerifiedAt()).isNotNull();
		assertThat(stored.getOnboardedAt()).isNull();
		assertThat(stored.getAvatarUrl()).isEqualTo("https://lh3.googleusercontent.com/a/" + sub);
	}
	
	@Test
	void returningUserKeepsIdentityAndDisplayNameButRefreshesGoogleFields() throws Exception {
		String sub = sub();
		JsonNode first = signIn(sub, email());
		String newEmail = email();
		MockHttpServletResponse again = google(FakeGoogleTokenVerifier.token(sub, newEmail, "Asha R. Rao"), freshIp());
		assertThat(again.getStatus()).isEqualTo(200);
		JsonNode second = body(again);
		assertThat(second.at("/user/id").asString()).isEqualTo(first.at("/user/id").asString());
		assertThat(second.at("/user/email").asString()).isEqualTo(newEmail);
		assertThat(second.at("/user/displayName").asString()).isEqualTo("Asha Rao");
		User stored = users.findByGoogleSub(sub).orElseThrow();
		assertThat(stored.getGoogleName()).isEqualTo("Asha R. Rao");
		assertThat(stored.getLastLoginAt()).isNotNull();
	}
	
	@Test
	void unverifiedEmailWrongAudienceAndGarbageAre401() throws Exception {
		for (String bad : new String[] {"fake-unverified:" + sub() + ":" + email(),
			"fake-aud:someone-elses-client:" + sub() + ":" + email(), "not-a-token"}) {
			MockHttpServletResponse response = google(bad, freshIp());
			assertThat(response.getStatus()).as(bad).isEqualTo(401);
			assertThat(body(response).get("code").asString()).isEqualTo("google_token_invalid");
			assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		}
		assertThat(google("fake-aud:fake-test-client-id:" + sub() + ":" + email(), freshIp()).getStatus())
			.isEqualTo(200);
		mvc.perform(post("/auth/google").with(freshIp()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnprocessableContent());
	}
	
	@Test
	void emailOwnedByAnotherUserIs409AndNeverMerged() throws Exception {
		User seed = data.user("Demo Tenant BLR-1");
		MockHttpServletResponse response = google(FakeGoogleTokenVerifier.token(sub(), seed.getEmail()), freshIp());
		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(body(response).get("code").asString()).isEqualTo("account_email_conflict");
		
		// An existing Google user whose email changes to someone else's email is refused too.
		String sub = sub();
		signIn(sub, email());
		MockHttpServletResponse changed = google(FakeGoogleTokenVerifier.token(sub, seed.getEmail()), freshIp());
		assertThat(changed.getStatus()).isEqualTo(409);
		assertThat(users.findByGoogleSub(sub).orElseThrow().getEmail()).isNotEqualTo(seed.getEmail());
	}
	
	private MockHttpServletResponse refresh(String token) throws Exception {
		return mvc.perform(post("/auth/refresh").cookie(refreshCookie(token))).andReturn().getResponse();
	}
	
	@Test
	void refreshRotatesAndReuseOfAnOlderTokenRevokesTheWholeFamily() throws Exception {
		MockHttpServletResponse signIn = google(FakeGoogleTokenVerifier.token(sub(), email()), freshIp());
		String first = refreshToken(signIn);
		
		MockHttpServletResponse refreshed = mvc.perform(post("/auth/refresh").cookie(refreshCookie(first)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.user.id").value(body(signIn).at("/user/id").asString()))
			.andReturn().getResponse();
		String second = refreshToken(refreshed);
		assertThat(second).isNotBlank().isNotEqualTo(first);
		String third = refreshToken(refresh(second));
		assertThat(third).isNotBlank().isNotEqualTo(second);
		
		// The first token is two rotations old: theft is assumed and the family is revoked.
		mvc.perform(post("/auth/refresh").cookie(refreshCookie(first)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("refresh_token_invalid"));
		assertThat(refresh(third).getStatus()).isEqualTo(401);
	}
	
	@Test
	void reuseOfTheLastRotatedTokenWithinGraceKeepsTheSuccessor() throws Exception {
		MockHttpServletResponse signIn = google(FakeGoogleTokenVerifier.token(sub(), email()), freshIp());
		String first = refreshToken(signIn);
		String second = refreshToken(refresh(first));
		
		// A second tab refreshes with the cookie it still has: no logout, no new rotation.
		MockHttpServletResponse racing = refresh(first);
		assertThat(racing.getStatus()).isEqualTo(200);
		assertThat(body(racing).get("accessToken").asString()).isNotBlank();
		assertThat(body(racing).at("/user/id").asString()).isEqualTo(body(signIn).at("/user/id").asString());
		assertThat(racing.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		
		String third = refreshToken(refresh(second));
		assertThat(third).isNotBlank().isNotEqualTo(second);
		assertThat(refresh(third).getStatus()).isEqualTo(200);
	}
	
	@Test
	void reuseOfTheLastRotatedTokenAfterGraceRevokesTheWholeFamily() throws Exception {
		String first = refreshToken(google(FakeGoogleTokenVerifier.token(sub(), email()), freshIp()));
		String second = refreshToken(refresh(first));
		RefreshToken rotated = refreshTokens.findByTokenHash(RefreshTokenService.hash(first)).orElseThrow();
		rotated.setRevokedAt(rotated.getRevokedAt().minusSeconds(31));
		refreshTokens.save(rotated);
		
		assertThat(refresh(first).getStatus()).isEqualTo(401);
		assertThat(refresh(second).getStatus()).isEqualTo(401);
	}
	
	@Test
	void onlyOneOfTwoRotationsOfTheSameTokenCanRevokeIt() throws Exception {
		String first = refreshToken(google(FakeGoogleTokenVerifier.token(sub(), email()), freshIp()));
		Long id = refreshTokens.findByTokenHash(RefreshTokenService.hash(first)).orElseThrow().getId();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		
		assertThat(transactions.<Integer>execute(tx -> refreshTokens.revokeIfActive(id, now))).isEqualTo(1);
		assertThat(transactions.<Integer>execute(tx -> refreshTokens.revokeIfActive(id, now))).isZero();
	}
	
	@Test
	void refreshWithoutOrWithUnknownCookieIs401() throws Exception {
		mvc.perform(post("/auth/refresh"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("refresh_token_invalid"));
		mvc.perform(post("/auth/refresh").cookie(refreshCookie("nope")))
			.andExpect(status().isUnauthorized());
	}
	
	@Test
	void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
		String token = refreshToken(google(FakeGoogleTokenVerifier.token(sub(), email()), freshIp()));
		mvc.perform(post("/auth/logout").cookie(refreshCookie(token)))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
		mvc.perform(post("/auth/refresh").cookie(refreshCookie(token))).andExpect(status().isUnauthorized());
		mvc.perform(post("/auth/logout")).andExpect(status().isNoContent());
	}
	
	@Test
	void suspendedUserCannotRefreshOrSignIn() throws Exception {
		String sub = sub();
		String email = email();
		String token = refreshToken(google(FakeGoogleTokenVerifier.token(sub, email), freshIp()));
		User user = users.findByGoogleSub(sub).orElseThrow();
		user.setStatus(UserStatus.SUSPENDED);
		users.save(user);
		mvc.perform(post("/auth/refresh").cookie(refreshCookie(token)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("account_suspended"));
		MockHttpServletResponse again = google(FakeGoogleTokenVerifier.token(sub, email), freshIp());
		assertThat(again.getStatus()).isEqualTo(403);
		assertThat(body(again).get("code").asString()).isEqualTo("account_suspended");
	}
	
	@Test
	void onboardingIsRequiredBeforeAnythingButMeAuthAndPublicReads() throws Exception {
		JsonNode auth = signIn(sub(), email());
		mvc.perform(get("/test-only/me").header(HttpHeaders.AUTHORIZATION, bearer(auth)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("onboarding_required"));
		mvc.perform(patch("/me").header(HttpHeaders.AUTHORIZATION, bearer(auth))
				.contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Asha\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("onboarding_required"));
		mvc.perform(get("/admin/anything").header(HttpHeaders.AUTHORIZATION, bearer(auth)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("onboarding_required"));
		mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(auth)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.needsOnboarding").value(true));
		mvc.perform(get("/cities").header(HttpHeaders.AUTHORIZATION, bearer(auth))).andExpect(status().isOk());
		mvc.perform(get("/auth/config").header(HttpHeaders.AUTHORIZATION, bearer(auth))).andExpect(status().isOk());
	}
	
	@Test
	void onboardingSetsRolesOnceAndReturnsAFreshToken() throws Exception {
		JsonNode auth = signIn(sub(), email());
		MockHttpServletResponse response = mvc.perform(post("/me/onboarding")
				.header(HttpHeaders.AUTHORIZATION, bearer(auth)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":[\"TENANT\"],\"homeCity\":\"blr\",\"acceptTerms\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.roles", hasSize(1)))
			.andExpect(jsonPath("$.user.roles[0]").value("TENANT"))
			.andExpect(jsonPath("$.user.homeCity").value("BLR"))
			.andExpect(jsonPath("$.user.needsOnboarding").value(false))
			.andExpect(jsonPath("$.user.verification.TENANT").value("DRAFT"))
			.andReturn().getResponse();
		JsonNode onboarded = body(response);
		assertThat(tokens.verify(onboarded.get("accessToken").asString()).roles()).containsExactly(Role.TENANT);
		
		mvc.perform(get("/test-only/me").header(HttpHeaders.AUTHORIZATION, bearer(onboarded)))
			.andExpect(status().isOk());
		mvc.perform(post("/me/onboarding").header(HttpHeaders.AUTHORIZATION, bearer(onboarded))
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"LANDLORD\"],\"acceptTerms\":true}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("already_onboarded"));
	}
	
	@Test
	void onboardingRejectsAdminRoleMissingTermsAndCitiesThatAreNotLive() throws Exception {
		JsonNode auth = signIn(sub(), email());
		mvc.perform(post("/me/onboarding").header(HttpHeaders.AUTHORIZATION, bearer(auth))
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"],\"acceptTerms\":true}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("role_not_allowed"));
		mvc.perform(post("/me/onboarding").header(HttpHeaders.AUTHORIZATION, bearer(auth))
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"TENANT\"],\"acceptTerms\":false}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("acceptTerms"));
		mvc.perform(post("/me/onboarding").header(HttpHeaders.AUTHORIZATION, bearer(auth))
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[],\"acceptTerms\":true}"))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(post("/me/onboarding").header(HttpHeaders.AUTHORIZATION, bearer(auth))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":[\"TENANT\"],\"homeCity\":\"XYZ\",\"acceptTerms\":true}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("city_not_live"));
		assertThat(users.findByPublicId(UUID.fromString(auth.at("/user/id").asString())).orElseThrow().getRoles())
			.isEmpty();
	}
	
	@Test
	void patchMeAddsARoleWithAFreshTokenAndEditsTheProfile() throws Exception {
		JsonNode onboarded = onboard(signIn(sub(), email()), "[\"TENANT\"]");
		MockHttpServletResponse response = mvc.perform(patch("/me").header(HttpHeaders.AUTHORIZATION, bearer(onboarded))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"  Asha R.  \",\"addRoles\":[\"LANDLORD\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.displayName").value("Asha R."))
			.andExpect(jsonPath("$.user.phone").doesNotExist())
			.andExpect(jsonPath("$.user.roles", hasSize(2)))
			.andExpect(jsonPath("$.user.verification.LANDLORD").value("DRAFT"))
			.andReturn().getResponse();
		assertThat(tokens.verify(body(response).get("accessToken").asString()).roles())
			.containsExactlyInAnyOrder(Role.TENANT, Role.LANDLORD);
		
		mvc.perform(patch("/me").header(HttpHeaders.AUTHORIZATION, bearer(onboarded))
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"98765\",\"displayName\":\"A\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors", hasSize(2)));
		// A new number goes through the code flow only (D-117), so PATCH /me cannot store an unverified phone.
		mvc.perform(patch("/me").header(HttpHeaders.AUTHORIZATION, bearer(onboarded))
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+919876543210\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("phone"))
			.andExpect(jsonPath("$.errors[0].code").value("phone_needs_otp"));
		mvc.perform(patch("/me").header(HttpHeaders.AUTHORIZATION, bearer(onboarded))
				.contentType(MediaType.APPLICATION_JSON).content("{\"addRoles\":[\"ADMIN\"]}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("role_not_allowed"));
		mvc.perform(patch("/me").header(HttpHeaders.AUTHORIZATION, bearer(onboarded))
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.phone").doesNotExist());
	}
	
	@Test
	void meNeedsAToken() throws Exception {
		mvc.perform(get("/me")).andExpect(status().isUnauthorized());
	}
	
	@Test
	void signInIsRateLimitedPerClientIp() throws Exception {
		RequestPostProcessor sameIp = ip("192.0.2.77");
		for (int i = 0; i < 20; i++) {
			assertThat(google("not-a-token", sameIp).getStatus()).isEqualTo(401);
		}
		MockHttpServletResponse limited = google(FakeGoogleTokenVerifier.token(sub(), email()), sameIp);
		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(body(limited).get("code").asString()).isEqualTo("rate_limited");
		assertThat(Long.parseLong(limited.getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
		// Another client is not affected.
		assertThat(google(FakeGoogleTokenVerifier.token(sub(), email()), ip("192.0.2.78")).getStatus())
			.isEqualTo(200);
	}
	
}
