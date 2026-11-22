package com.rentrix.rentrixserver.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

/** The real {@code prod} profile: no dev login, and Google sign-in off without {@code GOOGLE_CLIENT_ID}. */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:sqlite:target/test-data/rentrix-prod-${random.uuid}.db",
	"app.jwt.secret=prod-profile-test-secret-that-is-long-enough-0123456789",
	"app.auth.google.client-id=",
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DirtiesContext
class ProdProfileAuthTest {
	
	@Autowired
	MockMvc mvc;
	
	@Test
	void devLoginRoutesAre404() throws Exception {
		mvc.perform(get("/auth/dev-users")).andExpect(status().isNotFound());
		mvc.perform(post("/auth/dev-login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
			.andExpect(status().isNotFound());
	}
	
	@Test
	void configHasNoDevLoginAndNoGoogleClientId() throws Exception {
		mvc.perform(get("/auth/config"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.devLogin").value(false))
			.andExpect(jsonPath("$.googleClientId").doesNotExist());
	}
	
	@Test
	void googleSignInIs503WithoutAClientId() throws Exception {
		mvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"x\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("google_sign_in_disabled"));
	}
	
}
