package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phone codes through the Telegram Gateway (D-146), with {@link FakeTelegramGateway} on: Telegram is the default
 * channel, a confirmed code marks {@code TELEGRAM} verified, opens posting and is reported back to Telegram.
 */
@IntegrationTest
@TestPropertySource(properties = "app.telegram-gateway.fake=true")
class TelegramPhoneCodeTest {

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
	TelegramGatewayHolder telegram;

	/** A random Indian mobile number that never ends in 0000 (the fake's "no Telegram" number). */
	static String phone() {
		return "+919" + String.format("%08d", ThreadLocalRandom.current().nextLong(100_000_000L)) + "1";
	}

	private FakeTelegramGateway fake() {
		return (FakeTelegramGateway) telegram.gateway();
	}

	private String bearer(User user) {
		return "Bearer " + tokens.issue(user.getPublicId(), user.getRoles());
	}

	private MockHttpServletResponse start(User user, String body) throws Exception {
		return mvc.perform(post("/me/identity/phone/start").header(HttpHeaders.AUTHORIZATION, bearer(user))
			.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
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

	@Test
	void telegramIsTheDefaultChannelAndAConfirmedCodeVerifiesThePhoneAsTelegram() throws Exception {
		User user = data.user("Telegram user", Role.TENANT);
		String phone = phone();

		JsonNode identityBefore = body(mvc.perform(get("/me/identity").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andReturn().getResponse());
		assertThat(identityBefore.findValues("method").stream().map(JsonNode::asString)).contains("TELEGRAM");

		MockHttpServletResponse started = start(user, "{\"phone\":\"" + phone + "\"}");
		assertThat(started.getStatus()).as(started.getContentAsString()).isEqualTo(200);
		assertThat(body(started).get("delivery").asString()).isEqualTo("TELEGRAM");
		String challengeId = body(started).get("challengeId").asString();
		PhoneOtpChallenge challenge = challenges.findAll().stream()
			.filter(c -> c.getPublicId().equals(UUID.fromString(challengeId))).findFirst().orElseThrow();
		assertThat(challenge.getChannel()).isEqualTo(PhoneOtpChallenge.TELEGRAM);
		assertThat(challenge.getGatewayRequestId()).startsWith("fake-");
		// The full number and the code never reach the response.
		assertThat(started.getContentAsString()).doesNotContain(phone.substring(3));

		String code = fake().lastCode(phone);
		MockHttpServletResponse confirmed = confirm(user, challengeId, code);
		assertThat(confirmed.getStatus()).as(confirmed.getContentAsString()).isEqualTo(200);

		User reloaded = users.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getPhone()).isEqualTo(phone);
		IdentityVerification row = checks.findByUserAndMethod(reloaded, IdentityMethod.TELEGRAM).orElseThrow();
		assertThat(row.getStatus()).isEqualTo(IdentityStatus.VERIFIED);
		assertThat(checks.findByUserAndMethod(reloaded, IdentityMethod.PHONE_OTP)).isEmpty();
		assertThat(identity.isPhoneVerified(reloaded)).isTrue();
		assertThat(identity.hasPostingMethod(reloaded)).isTrue();
		assertThat(fake().reported(challenge.getGatewayRequestId(), code)).isTrue();

		// The same number again, on either channel, is already verified.
		assertThat(start(user, "{\"phone\":\"" + phone + "\",\"channel\":\"SMS\"}").getStatus()).isEqualTo(409);
	}

	@Test
	void smsStaysTheFallbackTheUserCanPick() throws Exception {
		User user = data.user("SMS fallback", Role.TENANT);
		MockHttpServletResponse started = start(user, "{\"phone\":\"" + phone() + "\",\"channel\":\"sms\"}");
		assertThat(started.getStatus()).as(started.getContentAsString()).isEqualTo(200);
		assertThat(body(started).get("delivery").asString()).isEqualTo("SMS");
	}

	@Test
	void aNumberWithoutTelegramUsesTheCodeUpAndSuggestsSms() throws Exception {
		User user = data.user("No Telegram", Role.TENANT);
		String phone = "+91987654" + FakeTelegramGateway.NO_TELEGRAM_SUFFIX;
		MockHttpServletResponse started = start(user, "{\"phone\":\"" + phone + "\",\"channel\":\"TELEGRAM\"}");
		assertThat(started.getStatus()).isEqualTo(503);
		assertThat(body(started).get("code").asString()).isEqualTo("telegram_send_failed");
		assertThat(body(started).get("detail").asString()).contains("SMS");
		assertThat(challenges.findFirstByPhoneE164OrderByCreatedAtDesc(phone).orElseThrow().getConsumedAt())
			.isNotNull();
	}

	@Test
	void anUnknownChannelIsAValidationError() throws Exception {
		User user = data.user("Odd channel", Role.TENANT);
		MockHttpServletResponse started = start(user, "{\"phone\":\"" + phone() + "\",\"channel\":\"PIGEON\"}");
		assertThat(started.getStatus()).isEqualTo(422);
	}

}
