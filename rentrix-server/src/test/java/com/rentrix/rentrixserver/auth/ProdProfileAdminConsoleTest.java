package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.auth.google.GoogleIdentity;
import com.rentrix.rentrixserver.auth.google.GoogleTokenVerifier;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static com.rentrix.rentrixserver.auth.AuthTestSupport.freshIp;
import static com.rentrix.rentrixserver.support.AdminConsole.adminBearer;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real {@code prod} profile (D-19, D-155): {@code ADMIN_CONSOLE_EMAILS} is the only way into the admin console. A
 * {@code user_roles} ADMIN row alone opens neither an admin sign-in nor an admin decision.
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:sqlite:target/test-data/rentrix-prod-admin-${random.uuid}.db",
	"app.jwt.secret=prod-profile-test-secret-that-is-long-enough-0123456789",
	"app.documents.encryption-key=prod-profile-test-document-key-long-enough-0123456789",
	"app.auth.google.client-id=prod-admin-test-client",
	"app.admin-console.hosts=admin.localhost",
	"app.admin-console.emails=listed-admin@example.test",
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DirtiesContext
class ProdProfileAdminConsoleTest {

	private static final String LISTED = "listed-admin@example.test";
	
	private static final String LISTED_SUB = "listed-admin-sub";

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	UserRepository users;

	@Autowired
	AccessTokenService tokens;

	@MockitoBean
	GoogleTokenVerifier google;

	/** Tokens are {@code <sub>:<email>}. */
	@BeforeEach
	void verifierReadsSubAndEmail() {
		given(google.verify(anyString())).willAnswer(call -> {
			String[] parts = call.<String>getArgument(0).split(":", 2);
			return new GoogleIdentity(parts[0], parts[1], true, "Admin", null);
		});
	}

	private User roleRowAdmin() {
		User user = data.user("Role-row admin " + UUID.randomUUID(), Role.ADMIN);
		user.setGoogleSub("sub-" + UUID.randomUUID());
		user.setEmailVerifiedAt(Instant.parse("2026-11-01T10:00:00Z"));
		return users.save(user);
	}

	private ResultActions adminGoogle(String sub, String email) throws Exception {
		return mvc.perform(post("/auth/admin/google").with(freshIp()).with(adminHost())
			.contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"" + sub + ":" + email + "\"}"));
	}

	private ResultActions approve(User admin) throws Exception {
		return mvc.perform(post("/admin/applications/" + UUID.randomUUID() + "/approve").with(adminHost())
			.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin))
			.contentType(MediaType.APPLICATION_JSON).content("{}"));
	}

	@Test
	void theRoleRowAloneDoesNotOpenTheConsoleSignIn() throws Exception {
		User unlisted = roleRowAdmin();
		adminGoogle(unlisted.getGoogleSub(), unlisted.getEmail())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_an_admin"));

		adminGoogle(LISTED_SUB, LISTED).andExpect(status().isOk());
	}

	@Test
	void anAdminTokenForAnUnlistedRoleRowAdminCannotDecide() throws Exception {
		approve(roleRowAdmin()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));

		adminGoogle(LISTED_SUB, LISTED).andExpect(status().isOk());
		User listed = users.findByEmail(LISTED).orElseThrow();
		approve(listed).andExpect(status().isNotFound());
	}

}
