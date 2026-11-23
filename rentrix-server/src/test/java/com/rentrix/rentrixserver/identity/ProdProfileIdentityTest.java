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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

/** {@code prod} with no SMS provider: the API starts, phone OTP is listed unavailable and answers 503 (D-101). */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:sqlite:target/test-data/rentrix-prod-identity-${random.uuid}.db",
	"app.jwt.secret=prod-profile-test-secret-that-is-long-enough-0123456789",
	"app.documents.encryption-key=prod-profile-test-document-key-long-enough-0123456789",
	"app.auth.google.client-id=",
	"app.identity.hash-key=",
	"app.sms.backend=",
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DirtiesContext
class ProdProfileIdentityTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	AccessTokenService tokens;
	
	@Test
	void phoneOtpIsUnavailableWithoutAnSmsProvider() throws Exception {
		User user = data.user("Prod user", Role.TENANT);
		String bearer = "Bearer " + tokens.issue(user.getPublicId(), user.getRoles());
		mvc.perform(get("/me/identity").header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.methods[2].method").value("PHONE_OTP"))
			.andExpect(jsonPath("$.methods[2].available").value(false));
		mvc.perform(post("/me/identity/phone/start").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+919876543210\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("phone_otp_unavailable"));
		mvc.perform(post("/me/identity/phone/confirm").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + UUID.randomUUID() + "\",\"code\":\"123456\"}"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("phone_otp_unavailable"));
		mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.idVerified").value(false));
	}
	
}
