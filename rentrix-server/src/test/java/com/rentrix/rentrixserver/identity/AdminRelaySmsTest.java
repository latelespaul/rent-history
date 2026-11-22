package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A preview deployment (D-137): {@code prod} with {@code PREVIEW_MODE=true} and {@code SMS_BACKEND=admin-relay}.
 * No SMS is sent; an admin reads the pending code from {@code GET /admin/sms-relay} and the user confirms with it.
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:sqlite:target/test-data/rentrix-admin-relay-${random.uuid}.db",
	"app.jwt.secret=prod-profile-test-secret-that-is-long-enough-0123456789",
	"app.auth.google.client-id=",
	"app.identity.hash-key=admin-relay-test-hash-key-that-is-long-enough-0123",
	"app.sms.backend=admin-relay",
	"app.preview-mode=true",
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DirtiesContext
class AdminRelaySmsTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	AccessTokenService tokens;
	
	@Autowired
	ObjectMapper json;
	
	@Autowired
	IdentityService identity;
	
	private String bearer(User user) {
		return "Bearer " + tokens.issue(user.getPublicId(), user.getRoles());
	}
	
	@Test
	void anAdminReadsTheRelayedCodeAndTheUserConfirmsWithIt() throws Exception {
		User user = data.user("Preview tenant", Role.TENANT);
		User admin = data.user("Preview admin", Role.ADMIN);
		String started = mvc.perform(post("/me/identity/phone/start").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+919812345678\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.delivery").value("TEAM"))
			.andReturn().getResponse().getContentAsString();
		assertThat(started).doesNotContain("\"code\"");
		
		mvc.perform(get("/admin/sms-relay").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isForbidden());
		JsonNode relayed = json.readTree(mvc.perform(get("/admin/sms-relay")
				.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].userId").value(user.getPublicId().toString()))
			.andExpect(jsonPath("$[0].phone").value("+91 XXXXXX5678"))
			.andReturn().getResponse().getContentAsString()).get(0);
		String code = relayed.get("code").asString();
		assertThat(code).matches("\\d{6}");
		
		String challengeId = json.readTree(started).get("challengeId").asString();
		mvc.perform(post("/me/identity/phone/confirm").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk());
		assertThat(identity.isPhoneVerified(user)).isTrue();
	}
	
}
