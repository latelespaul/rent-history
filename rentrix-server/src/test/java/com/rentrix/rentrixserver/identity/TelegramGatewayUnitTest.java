package com.rentrix.rentrixserver.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No real Telegram call: {@link HttpTelegramGateway} runs against a mock server bound to its client (D-146). */
class TelegramGatewayUnitTest {

	private static final TelegramGatewayProperties PROPERTIES = new TelegramGatewayProperties("test-token-not-real",
		URI.create("https://gateway.invalid"), false, null);

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final HttpTelegramGateway gateway = new HttpTelegramGateway(builder, PROPERTIES, JsonMapper.builder().build());

	@Test
	void sendPostsOurCodeWithTheBearerTokenAndReturnsTheRequestId() {
		server.expect(requestTo("https://gateway.invalid/sendVerificationMessage"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token-not-real"))
			.andExpect(content().json("{\"phone_number\":\"+919876543210\",\"code\":\"482913\",\"ttl\":600}"))
			.andRespond(withSuccess("{\"ok\":true,\"result\":{\"request_id\":\"r-1\",\"phone_number\":\"+919876543210\","
				+ "\"request_cost\":0.01}}", MediaType.APPLICATION_JSON));

		assertThat(gateway.send("+919876543210", "482913", Duration.ofMinutes(10))).isEqualTo("r-1");
		server.verify();
	}

	@Test
	void reportEnteredChecksTheCodeAgainstTheRequest() {
		server.expect(requestTo("https://gateway.invalid/checkVerificationStatus"))
			.andExpect(content().json("{\"request_id\":\"r-1\",\"code\":\"482913\"}"))
			.andRespond(withSuccess("{\"ok\":true,\"result\":{\"request_id\":\"r-1\","
				+ "\"verification_status\":{\"status\":\"code_valid\"}}}", MediaType.APPLICATION_JSON));

		gateway.reportEntered("r-1", "482913");
		server.verify();
	}

	@Test
	void aRefusalNamesTelegramsErrorButNeverTheTokenNumberOrCode() {
		server.expect(requestTo("https://gateway.invalid/sendVerificationMessage"))
			.andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON).body("{\"ok\":false,\"error\":\"PHONE_NUMBER_INVALID\"}"));

		assertThatThrownBy(() -> gateway.send("+919876543210", "482913", Duration.ofMinutes(10)))
			.isInstanceOf(TelegramGateway.GatewayException.class)
			.hasMessageContaining("PHONE_NUMBER_INVALID")
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain("test-token-not-real")
				.doesNotContain("9876543210").doesNotContain("482913"));
	}

	@Test
	void aServerErrorOrAnAnswerWithoutARequestIdFails() {
		server.expect(requestTo("https://gateway.invalid/sendVerificationMessage"))
			.andRespond(withServerError().body("+919876543210 482913"));
		assertThatThrownBy(() -> gateway.send("+919876543210", "482913", Duration.ofMinutes(10)))
			.isInstanceOf(TelegramGateway.GatewayException.class)
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain("9876543210").doesNotContain("482913"));

		server.reset();
		server.expect(requestTo("https://gateway.invalid/sendVerificationMessage"))
			.andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> gateway.send("+919876543210", "482913", Duration.ofMinutes(10)))
			.isInstanceOf(TelegramGateway.GatewayException.class)
			.hasMessageContaining("request_id");
	}

	@Test
	void propertiesNeverPrintTheToken() {
		assertThat(PROPERTIES.toString()).doesNotContain("test-token-not-real");
		assertThat(new TelegramGatewayProperties(" ", null, false, null).token()).isNull();
		assertThat(new TelegramGatewayProperties(null, null, false, null).baseUrl())
			.isEqualTo(TelegramGatewayProperties.DEFAULT_BASE_URL);
	}

}
