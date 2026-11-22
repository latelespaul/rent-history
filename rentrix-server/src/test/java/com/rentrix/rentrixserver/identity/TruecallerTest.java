package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.VerificationApplication;
import com.rentrix.rentrixserver.verification.VerificationApplicationRepository;
import com.rentrix.rentrixserver.verification.VerificationEvent;
import com.rentrix.rentrixserver.verification.VerificationEventRepository;
import com.rentrix.rentrixserver.verification.VerificationEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Truecaller phone verification on the test profile, where {@link FakeTruecallerVerifier} reads the access token
 * {@code fake:<phone>} as a verified number: the tests play Truecaller's server by posting to the callback.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class TruecallerTest {

	private static final String ENDPOINT = "https://profile4-noneu.truecaller.com/v1/default";

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
	VerificationApplicationRepository applications;

	@Autowired
	VerificationEventRepository events;

	@MockitoSpyBean
	PhoneVerifier verifier;

	/** A random Indian mobile number as Truecaller writes it: country code, no plus. */
	static String truecallerNumber() {
		return "919" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
	}

	private String bearer(User user) {
		return "Bearer " + tokens.issue(user.getPublicId(), user.getRoles());
	}

	private JsonNode body(MockHttpServletResponse response) throws Exception {
		return json.readTree(response.getContentAsString());
	}

	private JsonNode start(User user) throws Exception {
		MockHttpServletResponse response = mvc.perform(post("/me/identity/truecaller/start")
			.header(HttpHeaders.AUTHORIZATION, bearer(user))).andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		return body(response);
	}

	/** Truecaller's server posting to the callback: anonymous, JSON. */
	private int callback(String body) throws Exception {
		return mvc.perform(post("/auth/truecaller/callback").contentType(MediaType.APPLICATION_JSON).content(body))
			.andReturn().getResponse().getStatus();
	}

	private int token(String requestId, String accessToken, String endpoint) throws Exception {
		return callback("{\"requestId\":\"" + requestId + "\",\"accessToken\":\"" + accessToken
			+ "\",\"endpoint\":\"" + endpoint + "\"}");
	}

	private MockHttpServletResponse status(User user, String requestId) throws Exception {
		return mvc.perform(get("/me/identity/truecaller/" + requestId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andReturn().getResponse();
	}

	/** Polls like the web does until the request leaves {@code PENDING}, {@code INVOKED} and {@code VERIFYING}. */
	private JsonNode settled(User user, String requestId) throws Exception {
		JsonNode view = null;
		for (int i = 0; i < 200; i++) {
			view = body(status(user, requestId));
			String state = view.get("status").asString();
			if (!state.equals("PENDING") && !state.equals("INVOKED") && !state.equals("VERIFYING")) {
				return view;
			}
			Thread.sleep(25);
		}
		throw new AssertionError("Truecaller request never settled: " + view);
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
	void startReturnsTheDeepLinkAndTheMethodIsOffered() throws Exception {
		User user = data.user("Ira", Role.TENANT);
		JsonNode started = start(user);
		String requestId = started.get("requestId").asString();
		assertThat(requestId).matches("[A-Za-z0-9_-]{8,64}");
		assertThat(started.get("deepLink").asString())
			.startsWith("truecallersdk://truesdk/web_verify?type=btmsheet&requestNonce=" + requestId + "&")
			.contains("&partnerKey=test-only-fake-truecaller-key&", "&partnerName=Doorway%20Living&", "&lang=en",
				"&loginSuffix=verifymobile", "&skipOption=useanothermethod");
		assertThat(started.get("expiresAt").asString()).isNotBlank();
		assertThat(body(status(user, requestId)).get("status").asString()).isEqualTo("PENDING");

		JsonNode view = body(mvc.perform(get("/me/identity").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andReturn().getResponse());
		assertThat(method(view, "TRUECALLER").get("available").asBoolean()).isTrue();
	}

	@Test
	void acceptedConsentMarksThePhoneVerifiedAndWritesTheEvent(CapturedOutput output) throws Exception {
		User user = data.user("Kabir", Role.TENANT);
		VerificationApplication application = new VerificationApplication();
		application.setUser(user);
		application.setRole(Role.TENANT);
		applications.save(application);
		String number = truecallerNumber();
		String last4 = number.substring(number.length() - 4);
		assertThat(identity.isPhoneVerified(user)).isFalse();

		String requestId = start(user).get("requestId").asString();
		assertThat(callback("{\"requestId\":\"" + requestId + "\",\"status\":\"flow_invoked\"}")).isEqualTo(200);
		assertThat(body(status(user, requestId)).get("status").asString()).isEqualTo("INVOKED");
		String accessToken = FakeTruecallerVerifier.token(number);
		assertThat(token(requestId, accessToken, ENDPOINT)).isEqualTo(200);

		JsonNode settled = settled(user, requestId);
		assertThat(settled.get("status").asString()).isEqualTo("VERIFIED");
		assertThat(settled.get("masked").asString()).isEqualTo("+91 XXXXXX" + last4);

		User saved = users.findById(user.getId()).orElseThrow();
		assertThat(saved.getPhone()).isEqualTo("+" + number);
		assertThat(identity.isPhoneVerified(saved)).as("Truecaller counts as the verified phone").isTrue();
		IdentityVerification row = checks.findByUserAndMethod(saved, IdentityMethod.TRUECALLER).orElseThrow();
		assertThat(row.getStatus()).isEqualTo(IdentityStatus.VERIFIED);
		assertThat(row.getCheckedBy()).isEqualTo(CheckedBy.PROVIDER);
		assertThat(row.getProvider()).isEqualTo("TRUECALLER");
		assertThat(row.getLast4()).isEqualTo(last4);
		assertThat(row.getValueHmac()).hasSize(64).doesNotContain(number);
		assertThat(row.getVerifiedAt()).isNotNull();
		assertThat(row.getProviderRef()).isNull();

		JsonNode view = body(mvc.perform(get("/me/identity").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andReturn().getResponse());
		JsonNode truecaller = method(view, "TRUECALLER");
		assertThat(truecaller.get("status").asString()).isEqualTo("VERIFIED");
		assertThat(truecaller.get("masked").asString()).isEqualTo("+91 XXXXXX" + last4);
		assertThat(view.get("idVerified").asBoolean()).as("a phone alone gives no badge (D-100)").isFalse();

		List<VerificationEvent> history = events.findByApplicationOrderByCreatedAtAscIdAsc(application);
		assertThat(history).extracting(VerificationEvent::getEvent).containsExactly(VerificationEventType.PHONE_VERIFIED);
		assertThat(history.getFirst().getCode()).isEqualTo("TRUECALLER");

		assertThat(output.getAll()).doesNotContain(number).doesNotContain(number.substring(2))
			.doesNotContain(accessToken).doesNotContain("test-only-fake-truecaller-key");
	}

	@Test
	void aUsedNonceTakesNoSecondToken() throws Exception {
		User user = data.user("Meera", Role.LANDLORD);
		String first = truecallerNumber();
		String requestId = start(user).get("requestId").asString();
		token(requestId, FakeTruecallerVerifier.token(first), ENDPOINT);
		assertThat(settled(user, requestId).get("status").asString()).isEqualTo("VERIFIED");

		String second = truecallerNumber();
		assertThat(token(requestId, FakeTruecallerVerifier.token(second), ENDPOINT)).isEqualTo(200);
		Thread.sleep(100);

		verify(verifier, times(1)).fetchPhone(any(), anyString());
		assertThat(users.findById(user.getId()).orElseThrow().getPhone()).isEqualTo("+" + first);
		assertThat(body(status(user, requestId)).get("masked").asString())
			.isEqualTo("+91 XXXXXX" + first.substring(first.length() - 4));
	}

	@Test
	void aProfileEndpointOutsideTruecallerIsRefused() throws Exception {
		User user = data.user("Neel", Role.TENANT);
		for (String endpoint : List.of("https://evil.example.com/v1/default", "http://profile4.truecaller.com/v1/default",
			"https://profile4.truecaller.com.evil.example/v1/default", "https://user@profile4.truecaller.com/v1/default",
			"https://profile4.truecaller.com:8443/v1/default", "not a url")) {
			String requestId = start(user).get("requestId").asString();
			assertThat(token(requestId, FakeTruecallerVerifier.token(truecallerNumber()), endpoint)).isEqualTo(200);
			JsonNode settled = settled(user, requestId);
			assertThat(settled.get("status").asString()).as(endpoint).isEqualTo("FAILED");
			assertThat(settled.get("reason").asString()).isEqualTo("endpoint_refused");
		}
		verify(verifier, never()).fetchPhone(any(), anyString());
		assertThat(identity.isPhoneVerified(users.findById(user.getId()).orElseThrow())).isFalse();
	}

	@Test
	void declinedFailedAndForeignRequestsFallBack() throws Exception {
		User user = data.user("Om", Role.TENANT);
		String declined = start(user).get("requestId").asString();
		callback("{\"requestId\":\"" + declined + "\",\"status\":\"user_rejected\"}");
		JsonNode rejected = body(status(user, declined));
		assertThat(rejected.get("status").asString()).isEqualTo("REJECTED");
		assertThat(rejected.get("reason").asString()).isEqualTo("user_rejected");

		String badToken = start(user).get("requestId").asString();
		token(badToken, "not-a-fake-token", ENDPOINT);
		assertThat(settled(user, badToken).get("reason").asString()).isEqualTo("profile_unavailable");

		String foreignNumber = start(user).get("requestId").asString();
		token(foreignNumber, FakeTruecallerVerifier.token("14155550123"), ENDPOINT);
		assertThat(settled(user, foreignNumber).get("reason").asString()).isEqualTo("not_indian_mobile");

		User other = data.user("Pia", Role.TENANT);
		String mine = start(user).get("requestId").asString();
		assertThat(status(other, mine).getStatus()).isEqualTo(404);
		assertThat(callback("{\"requestId\":\"unknown-request-id\",\"status\":\"flow_invoked\"}")).isEqualTo(200);
		assertThat(callback("not json")).isEqualTo(400);
		assertThat(identity.isPhoneVerified(users.findById(user.getId()).orElseThrow())).isFalse();
	}

}
