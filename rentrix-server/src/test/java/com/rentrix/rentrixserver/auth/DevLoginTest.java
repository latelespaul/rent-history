package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.freshIp;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshCookie;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

/**
 * Dev login with {@code DEV_LOGIN_ENABLED=true} on the test profile (D-77). The admin console's dev login is in
 * {@link AdminConsoleAuthTest}, which shares this context.
 */
@SpringBootTest(properties = {"app.dev-login.enabled=true", "app.admin-console.emails=console-admin@example.test,listed-demo@example.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevLoginTest {
	
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
	
	private User demo(String name, Role... roles) {
		User user = data.user(name, roles);
		user.setDemo(true);
		user.setEmailVerifiedAt(Instant.parse("2024-01-01T00:00:00Z"));
		if (roles.length > 0) {
			user.setOnboardedAt(Instant.parse("2024-01-01T00:00:00Z"));
		}
		return users.save(user);
	}
	
	private MockHttpServletResponse devLogin(UUID id) throws Exception {
		return mvc.perform(post("/auth/dev-login").with(freshIp()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":\"" + id + "\"}"))
			.andReturn().getResponse();
	}
	
	@Test
	void configAdvertisesDevLogin() throws Exception {
		mvc.perform(get("/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("$.devLogin").value(true));
	}
	
	@Test
	void devUsersListsOnlyActiveDemoUsers() throws Exception {
		User demo = demo("Demo Landlord BLR-1", Role.LANDLORD);
		User suspended = demo("Demo Tenant BLR-9", Role.TENANT);
		suspended.setStatus(UserStatus.SUSPENDED);
		users.save(suspended);
		User real = data.user("Real Person", Role.TENANT);
		String list = mvc.perform(get("/auth/dev-users")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(list).contains(demo.getPublicId().toString(), "Demo Landlord BLR-1")
			.doesNotContain(real.getPublicId().toString(), suspended.getPublicId().toString());
	}
	
	@Test
	void devLoginSignsInADemoUserWithACookie() throws Exception {
		User demo = demo("Demo Tenant PNQ-3", Role.TENANT);
		MockHttpServletResponse response = devLogin(demo.getPublicId());
		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode body = json.readTree(response.getContentAsString());
		assertThat(body.get("needsOnboarding").asBoolean()).isFalse();
		assertThat(body.at("/user/demo").asBoolean()).isTrue();
		// The one short-name rule: the seed code "PNQ-3" is skipped, so not "Demo P.".
		assertThat(body.at("/user/shortName").asString()).isEqualTo("Demo T.");
		assertThat(tokens.verify(body.get("accessToken").asString()).roles()).containsExactly(Role.TENANT);
		mvc.perform(post("/auth/refresh").cookie(refreshCookie(refreshToken(response)))).andExpect(status().isOk());
	}
	
	@Test
	void devLoginAsANewUserStartsTheFirstSignIn() throws Exception {
		MockHttpServletResponse response = mvc.perform(post("/auth/dev-login/new").with(freshIp()))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(200);
		JsonNode body = json.readTree(response.getContentAsString());
		assertThat(body.get("needsOnboarding").asBoolean()).isTrue();
		assertThat(body.at("/user/demo").asBoolean()).isFalse();
		assertThat(body.at("/user/displayName").asString()).isEqualTo("New User");
		assertThat(tokens.verify(body.get("accessToken").asString()).roles()).isEmpty();
		// Each call is a different person, and none of them shows up in the seed-user list.
		JsonNode second = json.readTree(mvc.perform(post("/auth/dev-login/new").with(freshIp()))
			.andReturn().getResponse().getContentAsString());
		assertThat(second.at("/user/id").asString()).isNotEqualTo(body.at("/user/id").asString());
		assertThat(mvc.perform(get("/auth/dev-users")).andReturn().getResponse().getContentAsString())
			.doesNotContain(body.at("/user/id").asString());
		// It can sign out and back in (the e2e sign-up gate does), as with Google.
		assertThat(devLogin(UUID.fromString(body.at("/user/id").asString())).getStatus()).isEqualTo(200);
	}
	
	@Test
	void devLoginRefusesRealUsersUnknownIdsAndSuspendedDemoUsers() throws Exception {
		assertThat(devLogin(data.user("Real Person", Role.TENANT).getPublicId()).getStatus()).isEqualTo(404);
		assertThat(devLogin(UUID.randomUUID()).getStatus()).isEqualTo(404);
		User suspended = demo("Demo Tenant BLR-8", Role.TENANT);
		suspended.setStatus(UserStatus.SUSPENDED);
		users.save(suspended);
		assertThat(devLogin(suspended.getPublicId()).getStatus()).isEqualTo(403);
	}
	
	@Test
	void aListedConsoleEmailGetsNoAdminOnTheMainSite() throws Exception {
		User listed = demo("Demo Console " + UUID.randomUUID(), Role.TENANT);
		listed.setEmail("listed-demo@example.test");
		users.save(listed);
		JsonNode body = json.readTree(devLogin(listed.getPublicId()).getContentAsString());
		assertThat(tokens.verify(body.get("accessToken").asString()).roles()).containsExactly(Role.TENANT);
		assertThat(body.at("/user/roles").toString()).doesNotContain("ADMIN");
		mvc.perform(get("/admin/anything").header(HttpHeaders.AUTHORIZATION, "Bearer " + body.get("accessToken").asString()))
			.andExpect(status().isNotFound());
	}
	
}
