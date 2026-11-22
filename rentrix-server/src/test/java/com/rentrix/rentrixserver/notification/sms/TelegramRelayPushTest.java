package com.rentrix.rentrixserver.notification.sms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The optional Telegram push of admin-relay codes (D-137). No real Telegram is called: the client runs against a
 * mock server, and the push runs on the calling thread.
 */
@ExtendWith(OutputCaptureExtension.class)
class TelegramRelayPushTest {
	
	private static final String PHONE = "+919876543210";
	private static final String TOKEN = "123456:test-token-not-real";
	private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	
	private static TelegramRelayPush push(RestClient.Builder builder, String chatIds) {
		return new TelegramRelayPush(builder, new SmsProperties.Telegram(TOKEN, chatIds), "Doorway Living",
			phone -> PHONE.equals(phone) ? "Asha Rao" : null, CLOCK, Runnable::run);
	}
	
	@Test
	void sendsTheCodeWithTheNameAndMaskedPhoneToEveryChat(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		for (String chat : new String[] {"111", "-222"}) {
			server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"chat_id\":\"" + chat + "\",\"text\":"
					+ "\"Doorway Living OTP for Asha Rao (+91 XXXXXX3210): 482913 — expires 10 min\"}"))
				.andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
		}
		push(builder, " 111, -222 ,").push(PHONE, "482913", NOW.plus(Duration.ofMinutes(10)));
		server.verify();
		assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("482913").doesNotContain("9876543210");
	}
	
	@Test
	void aTelegramFailureIsLoggedWithoutTheTokenAndDoesNotThrow(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.telegram.org/bot" + TOKEN + "/sendMessage"))
			.andRespond(withServerError());
		push(builder, "111").push(PHONE, "482913", NOW.plus(Duration.ofMinutes(10)));
		server.verify();
		assertThat(output.getAll()).contains("Telegram relay push failed for +91 XXXXXX3210")
			.doesNotContain(TOKEN).doesNotContain("test-token").doesNotContain("482913");
	}
	
	@Test
	void settingsNeverPrintTheTokenAndNeedBothValues() {
		assertThat(new SmsProperties.Telegram(TOKEN, "111").toString()).doesNotContain("test-token");
		assertThat(new SmsProperties.Telegram(TOKEN, "111").enabled()).isTrue();
		assertThat(new SmsProperties.Telegram(TOKEN, " , ").enabled()).isFalse();
		assertThat(new SmsProperties.Telegram(null, "111").enabled()).isFalse();
	}
	
}
