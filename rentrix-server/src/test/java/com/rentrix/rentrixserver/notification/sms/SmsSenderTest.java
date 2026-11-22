package com.rentrix.rentrixserver.notification.sms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No real provider is ever called: {@link HttpSmsSender} runs against a mock server bound to its client. */
@ExtendWith(OutputCaptureExtension.class)
class SmsSenderTest {
	
	private static final SmsProperties.Http HTTP =
		new SmsProperties.Http("https://sms.invalid/send", "test-key-not-real", "RNTRIX", null);
	
	@Test
	void httpSenderPostsJsonWithTheBearerKey(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://sms.invalid/send"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key-not-real"))
			.andExpect(content().json("{\"to\":\"+919876543210\",\"sender\":\"RNTRIX\","
				+ "\"message\":\"123456 is your Rentrix verification code.\"}"))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		new HttpSmsSender(builder, HTTP).send("+919876543210", "123456 is your Rentrix verification code.");
		server.verify();
		assertThat(output.getAll()).doesNotContain("123456").doesNotContain("9876543210");
	}
	
	@Test
	void httpSenderFailureKeepsTheNumberAndTextOutOfTheError() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://sms.invalid/send")).andRespond(withServerError().body("+919876543210 123456"));
		HttpSmsSender sender = new HttpSmsSender(builder, HTTP);
		assertThatThrownBy(() -> sender.send("+919876543210", "123456 is your Rentrix verification code."))
			.isInstanceOf(SmsSendException.class)
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain("9876543210").doesNotContain("123456"));
	}
	
	@Test
	void logSenderMasksTheNumber(CapturedOutput output) {
		new LogSmsSender().send("+919876543210", "654321 is your Rentrix verification code.");
		assertThat(output.getAll()).contains("SMS to +91 XXXXXX3210", "654321").doesNotContain("9876543210");
	}
	
	@Test
	void httpSettingsNeverPrintTheKey() {
		assertThat(HTTP.toString()).doesNotContain("test-key-not-real");
	}
	
}
