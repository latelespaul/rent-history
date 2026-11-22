package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

/** "ID verified" = any one of Aadhaar, PAN or phone OTP (D-99); LinkedIn is a bonus badge only (D-100, D-93). */
@IntegrationTest
class IdentityApiTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	TestData data;
	
	@Autowired
	AccessTokenService tokens;
	
	@Autowired
	IdentityService identity;
	
	@Autowired
	IdentityVerificationRepository checks;
	
	private String bearer(User user) {
		return "Bearer " + tokens.issue(user.getPublicId(), user.getRoles());
	}
	
	private IdentityVerification check(User user, IdentityMethod method, IdentityStatus status, String last4) {
		IdentityVerification row = new IdentityVerification();
		row.setUser(user);
		row.setMethod(method);
		row.setStatus(status);
		row.setLast4(last4);
		if (status == IdentityStatus.VERIFIED) {
			row.setCheckedBy(method == IdentityMethod.PHONE_OTP ? CheckedBy.OTP : CheckedBy.ADMIN);
			row.setVerifiedAt(Instant.parse("2026-11-01T10:00:00Z"));
		}
		return checks.save(row);
	}
	
	@Test
	void anyOneIdMethodVerifiedMakesTheUserIdVerified() {
		for (IdentityMethod method : IdentityService.ID_METHODS) {
			User user = data.user("Verified by " + method, Role.TENANT);
			check(user, method, IdentityStatus.VERIFIED, method == IdentityMethod.PAN ? "234F" : "3210");
			assertThat(identity.isIdVerified(user)).as(method.name()).isTrue();
			assertThat(identity.badges(user)).isEqualTo(new Badges(true, false));
		}
	}
	
	@Test
	void pendingRejectedOrRevokedMethodsDoNotCount() {
		User user = data.user("Waiting", Role.LANDLORD);
		check(user, IdentityMethod.PAN, IdentityStatus.PENDING, "234F");
		check(user, IdentityMethod.AADHAAR, IdentityStatus.REJECTED, "1234");
		check(user, IdentityMethod.PHONE_OTP, IdentityStatus.REVOKED, "3210");
		assertThat(identity.isIdVerified(user)).isFalse();
		assertThat(identity.badges(user)).isEqualTo(Badges.NONE);
	}
	
	@Test
	void linkedinAloneLeavesIdVerifiedFalse() throws Exception {
		User user = data.user("Linked", Role.LANDLORD);
		check(user, IdentityMethod.LINKEDIN, IdentityStatus.VERIFIED, null);
		assertThat(identity.isIdVerified(user)).isFalse();
		mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.idVerified").value(false))
			.andExpect(jsonPath("$.badges.linkedinVerified").value(true));
	}
	
	@Test
	void bothBadgesWhenAnIdMethodAndLinkedinAreVerified() throws Exception {
		User user = data.user("Both", Role.TENANT, Role.LANDLORD);
		check(user, IdentityMethod.AADHAAR, IdentityStatus.VERIFIED, "1234");
		check(user, IdentityMethod.LINKEDIN, IdentityStatus.VERIFIED, null);
		mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.idVerified").value(true))
			.andExpect(jsonPath("$.badges.linkedinVerified").value(true));
	}
	
	@Test
	void meIdentityListsEveryMethodMaskedOnly() throws Exception {
		User user = data.user("Masked", Role.TENANT);
		check(user, IdentityMethod.PAN, IdentityStatus.VERIFIED, "234F");
		check(user, IdentityMethod.AADHAAR, IdentityStatus.PENDING, "1234");
		mvc.perform(get("/me/identity").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.idVerified").value(true))
			.andExpect(jsonPath("$.linkedinVerified").value(false))
			.andExpect(jsonPath("$.methods.length()").value(4))
			.andExpect(jsonPath("$.methods[0].method").value("AADHAAR"))
			.andExpect(jsonPath("$.methods[0].status").value("PENDING"))
			.andExpect(jsonPath("$.methods[0].masked").value("XXXX XXXX 1234"))
			.andExpect(jsonPath("$.methods[1].method").value("PAN"))
			.andExpect(jsonPath("$.methods[1].masked").value("XXXXXX234F"))
			.andExpect(jsonPath("$.methods[1].checkedBy").value("ADMIN"))
			.andExpect(jsonPath("$.methods[1].available").value(false))
			.andExpect(jsonPath("$.methods[2].method").value("PHONE_OTP"))
			.andExpect(jsonPath("$.methods[2].status").doesNotExist())
			.andExpect(jsonPath("$.methods[2].available").value(true))
			.andExpect(jsonPath("$.methods[3].method").value("LINKEDIN"))
			.andExpect(jsonPath("$.methods[3].available").value(true));
	}
	
	@Test
	void linkedinIsNormalisedQueuedForAnAdminAndCanBeRemoved() throws Exception {
		User user = data.user("Profile", Role.LANDLORD);
		mvc.perform(put("/me/identity/linkedin").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"in.linkedin.com/in/Asha-Rao-123/?trk=public_profile\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.methods[3].status").value("PENDING"))
			.andExpect(jsonPath("$.methods[3].masked").value("https://www.linkedin.com/in/asha-rao-123"))
			.andExpect(jsonPath("$.linkedinVerified").value(false))
			.andExpect(jsonPath("$.idVerified").value(false));
		IdentityVerification row = checks.findByUserAndMethod(user, IdentityMethod.LINKEDIN).orElseThrow();
		assertThat(row.getLinkedinUrl()).isEqualTo("https://www.linkedin.com/in/asha-rao-123");
		assertThat(row.getLast4()).isNull();
		assertThat(row.getValueHmac()).isNull();
		
		mvc.perform(put("/me/identity/linkedin").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com/in/asha\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("url"));
		
		mvc.perform(delete("/me/identity/linkedin").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isNoContent());
		assertThat(checks.findByUserAndMethod(user, IdentityMethod.LINKEDIN)).isEmpty();
	}
	
	@Test
	void resendingTheVerifiedLinkedinUrlKeepsTheBadge() throws Exception {
		User user = data.user("Kept", Role.TENANT);
		IdentityVerification row = check(user, IdentityMethod.LINKEDIN, IdentityStatus.VERIFIED, null);
		row.setLinkedinUrl("https://www.linkedin.com/in/kept-user");
		checks.save(row);
		mvc.perform(put("/me/identity/linkedin").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://www.linkedin.com/in/kept-user/\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.linkedinVerified").value(true));
		mvc.perform(put("/me/identity/linkedin").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://www.linkedin.com/in/someone-else\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.linkedinVerified").value(false))
			.andExpect(jsonPath("$.methods[3].status").value("PENDING"));
	}
	
	@Test
	void adminReviewProviderQueuesAndReportsTheCheck() {
		User user = data.user("Queued", Role.TENANT);
		IdentityVerification row = new IdentityVerification();
		row.setUser(user);
		row.setMethod(IdentityMethod.PAN);
		row.setLast4("234F");
		String ref = new AdminReviewProvider(checks).start(row);
		assertThat(new AdminReviewProvider(checks).result(ref)).contains(IdentityStatus.PENDING);
		assertThat(new AdminReviewProvider(checks).result("not-a-ref")).isEmpty();
	}
	
}
