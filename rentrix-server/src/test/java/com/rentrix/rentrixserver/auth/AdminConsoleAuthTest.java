package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.auth.google.FakeGoogleTokenVerifier;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.freshIp;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshCookie;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshToken;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The admin console on its own host (D-19, §13.3): admin sign-in only on {@code ADMIN_HOSTS} and only for
 * {@code ADMIN_CONSOLE_EMAILS} or a {@code user_roles} admin, its own cookie and refresh family, and no admin role on the
 * main site. Same context as {@link DevLoginTest}.
 */
@SpringBootTest(properties = {"app.dev-login.enabled=true", "app.admin-console.emails=console-admin@example.test,listed-demo@example.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminConsoleAuthTest {
	
	private static final String LISTED = "console-admin@example.test";
	private static final String LISTED_SUB = "console-admin-sub";
	private static final Pattern ADMIN_COOKIE = Pattern.compile(RefreshCookies.ADMIN_NAME + "=([^;]*)");
	
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
	
	private MockHttpServletResponse google(String path, String idToken, RequestPostProcessor host) throws Exception {
		return mvc.perform(post(path).with(freshIp()).with(host).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("idToken", idToken))))
			.andReturn().getResponse();
	}
	
	private static RequestPostProcessor mainHost() {
		return request -> request;
	}
	
	private MockHttpServletResponse adminGoogle(String sub, String email) throws Exception {
		return google("/auth/admin/google", FakeGoogleTokenVerifier.token(sub, email, "Console Admin"), adminHost());
	}
	
	private JsonNode body(MockHttpServletResponse response) throws Exception {
		return json.readTree(response.getContentAsString());
	}
	
	private static String adminRefreshToken(MockHttpServletResponse response) {
		String header = response.getHeader(HttpHeaders.SET_COOKIE);
		Matcher m = ADMIN_COOKIE.matcher(header == null ? "" : header);
		return m.find() ? m.group(1) : null;
	}
	
	private static String bearer(JsonNode auth) {
		return "Bearer " + auth.get("accessToken").asString();
	}
	
	private User demo(String name, Role... roles) {
		User user = data.user(name, roles);
		user.setDemo(true);
		user.setEmailVerifiedAt(Instant.parse("2024-01-01T00:00:00Z"));
		return users.save(user);
	}
	
	private MockHttpServletResponse adminDevLogin(UUID id, RequestPostProcessor host) throws Exception {
		return mvc.perform(post("/auth/admin/dev-login").with(freshIp()).with(host)
				.contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + id + "\"}"))
			.andReturn().getResponse();
	}
	
	@Test
	void googleSignInOnTheAdminHostIsOnlyForListedEmailsAndCreatesNoStranger() throws Exception {
		String stranger = "stranger-" + UUID.randomUUID() + "@example.test";
		MockHttpServletResponse refused = adminGoogle("sub-" + UUID.randomUUID(), stranger);
		assertThat(refused.getStatus()).isEqualTo(403);
		assertThat(body(refused).get("code").asString()).isEqualTo("not_an_admin");
		assertThat(users.findByEmail(stranger)).isEmpty();
		
		MockHttpServletResponse response = adminGoogle(LISTED_SUB, LISTED.toUpperCase());
		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode auth = body(response);
		AuthenticatedUser caller = tokens.verify(auth.get("accessToken").asString());
		assertThat(caller.adminSession()).isTrue();
		assertThat(caller.roles()).containsExactly(Role.ADMIN);
		// The admin role is the session's, never the account's: the user view and the row hold no ADMIN.
		assertThat(auth.at("/user/roles").toString()).doesNotContain("ADMIN");
		assertThat(users.findByEmail(LISTED).orElseThrow().getRoles()).doesNotContain(Role.ADMIN);
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith(RefreshCookies.ADMIN_NAME + "=adm.")
			.contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth/admin");
		
		// On the main site the same person is a normal user: no admin role, onboarding as usual.
		MockHttpServletResponse main = google("/auth/google",
			FakeGoogleTokenVerifier.token(LISTED_SUB, LISTED, "Console Admin"), mainHost());
		assertThat(main.getStatus()).isEqualTo(200);
		AuthenticatedUser mainCaller = tokens.verify(body(main).get("accessToken").asString());
		assertThat(mainCaller.adminSession()).isFalse();
		assertThat(mainCaller.roles()).doesNotContain(Role.ADMIN);
		assertThat(main.getHeader(HttpHeaders.SET_COOKIE)).startsWith(RefreshCookies.NAME + "=");
	}
	
	@Test
	void eachSessionWorksOnlyOnItsOwnHost() throws Exception {
		JsonNode admin = body(adminGoogle(LISTED_SUB, LISTED));
		JsonNode main = body(google("/auth/google",
			FakeGoogleTokenVerifier.token("sub-" + UUID.randomUUID(), "main-" + UUID.randomUUID() + "@example.test"),
			mainHost()));
		
		mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("token_invalid"));
		mvc.perform(get("/me").with(adminHost()).header(HttpHeaders.AUTHORIZATION, bearer(main)))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("token_invalid"));
		mvc.perform(get("/me").with(adminHost()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk());
		// The console reads, but changes records only through /admin/**.
		mvc.perform(post("/me/onboarding").with(adminHost()).header(HttpHeaders.AUTHORIZATION, bearer(admin))
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"TENANT\"],\"acceptTerms\":true}"))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("admin_console_only"));
		
		// /admin/** and the admin sign-in do not exist on the main site, whatever the token.
		mvc.perform(get("/admin/sms-relay").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/admin/sms-relay")).andExpect(status().isNotFound());
		mvc.perform(post("/auth/admin/refresh")).andExpect(status().isNotFound());
		// On the admin host they do (anonymous: 401), and the main site's session endpoints do not.
		mvc.perform(get("/admin/sms-relay").with(adminHost())).andExpect(status().isUnauthorized());
		mvc.perform(post("/auth/refresh").with(adminHost())).andExpect(status().isNotFound());
		assertThat(google("/auth/google", FakeGoogleTokenVerifier.token(LISTED_SUB, LISTED), adminHost()).getStatus())
			.isEqualTo(404);
		mvc.perform(get("/auth/dev-users").with(adminHost())).andExpect(status().isNotFound());
		mvc.perform(get("/auth/config").with(adminHost())).andExpect(status().isOk());
	}
	
	@Test
	void adminRefreshKeepsItsOwnFamilyAndTheMainRefreshRefusesIt() throws Exception {
		MockHttpServletResponse signIn = adminGoogle(LISTED_SUB, LISTED);
		String adminRaw = adminRefreshToken(signIn);
		assertThat(adminRaw).startsWith("adm.");
		
		// An admin token sent as the main cookie is refused, and does not burn the admin family.
		mvc.perform(post("/auth/refresh").cookie(refreshCookie(adminRaw))).andExpect(status().isUnauthorized());
		MockHttpServletResponse refreshed = mvc.perform(post("/auth/admin/refresh").with(adminHost())
				.cookie(new Cookie(RefreshCookies.ADMIN_NAME, adminRaw)))
			.andReturn().getResponse();
		assertThat(refreshed.getStatus()).isEqualTo(200);
		assertThat(tokens.verify(body(refreshed).get("accessToken").asString()).adminSession()).isTrue();
		assertThat(adminRefreshToken(refreshed)).startsWith("adm.").isNotEqualTo(adminRaw);
		
		// A main-site token sent as the admin cookie is refused too.
		MockHttpServletResponse main = google("/auth/google", FakeGoogleTokenVerifier.token(LISTED_SUB, LISTED),
			mainHost());
		mvc.perform(post("/auth/admin/refresh").with(adminHost())
				.cookie(new Cookie(RefreshCookies.ADMIN_NAME, refreshToken(main))))
			.andExpect(status().isUnauthorized());
		
		MockHttpServletResponse logout = mvc.perform(post("/auth/admin/logout").with(adminHost())
				.cookie(new Cookie(RefreshCookies.ADMIN_NAME, adminRefreshToken(refreshed))))
			.andReturn().getResponse();
		assertThat(logout.getStatus()).isEqualTo(204);
		assertThat(logout.getHeader(HttpHeaders.SET_COOKIE)).startsWith(RefreshCookies.ADMIN_NAME + "=")
			.contains("Max-Age=0", "Path=/auth/admin");
		mvc.perform(post("/auth/admin/refresh").with(adminHost())
				.cookie(new Cookie(RefreshCookies.ADMIN_NAME, adminRefreshToken(refreshed))))
			.andExpect(status().isUnauthorized());
	}
	
	@Test
	void theSeedAdminSignsInOnlyOnTheAdminHostAndLosesItWithTheRole() throws Exception {
		User seedAdmin = demo("Demo Admin " + UUID.randomUUID(), Role.ADMIN);
		User demoTenant = demo("Demo Tenant " + UUID.randomUUID(), Role.TENANT);
		
		String mainList = mvc.perform(get("/auth/dev-users")).andReturn().getResponse().getContentAsString();
		assertThat(mainList).contains(demoTenant.getPublicId().toString())
			.doesNotContain(seedAdmin.getPublicId().toString());
		assertThat(mvc.perform(post("/auth/dev-login").with(freshIp()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":\"" + seedAdmin.getPublicId() + "\"}")).andReturn().getResponse().getStatus())
			.isEqualTo(404);
		
		String adminList = mvc.perform(get("/auth/admin/dev-users").with(adminHost()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(adminList).contains(seedAdmin.getPublicId().toString())
			.doesNotContain(demoTenant.getPublicId().toString());
		mvc.perform(get("/auth/admin/dev-users")).andExpect(status().isNotFound());
		assertThat(adminDevLogin(seedAdmin.getPublicId(), mainHost()).getStatus()).isEqualTo(404);
		assertThat(adminDevLogin(demoTenant.getPublicId(), adminHost()).getStatus()).isEqualTo(404);
		
		MockHttpServletResponse signIn = adminDevLogin(seedAdmin.getPublicId(), adminHost());
		assertThat(signIn.getStatus()).isEqualTo(200);
		assertThat(tokens.verify(body(signIn).get("accessToken").asString()).adminSession()).isTrue();
		
		seedAdmin.getRoles().clear();
		users.save(seedAdmin);
		MockHttpServletResponse refused = mvc.perform(post("/auth/admin/refresh").with(adminHost())
				.cookie(new Cookie(RefreshCookies.ADMIN_NAME, adminRefreshToken(signIn))))
			.andReturn().getResponse();
		assertThat(refused.getStatus()).isEqualTo(403);
		assertThat(body(refused).get("code").asString()).isEqualTo("not_an_admin");
	}
	
}
