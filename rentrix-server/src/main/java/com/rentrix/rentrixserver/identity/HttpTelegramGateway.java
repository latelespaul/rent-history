package com.rentrix.rentrixserver.identity;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * The real Telegram Gateway client: {@code POST <base>/sendVerificationMessage} and
 * {@code POST <base>/checkVerificationStatus}, JSON bodies, {@code Authorization: Bearer <token>}. Answers are
 * {@code {"ok": true, "result": RequestStatus}} or {@code {"ok": false, "error": "..."}}. Built by
 * {@link TelegramGatewayConfig} when {@code TELEGRAM_GATEWAY_TOKEN} is set.
 */
public class HttpTelegramGateway implements TelegramGateway {

	private final RestClient client;
	private final ObjectMapper json;
	private final URI base;
	private final String token;

	public HttpTelegramGateway(TelegramGatewayProperties properties, ObjectMapper json) {
		this(RestClient.builder().requestFactory(requests(properties.timeout())), properties, json);
	}

	HttpTelegramGateway(RestClient.Builder builder, TelegramGatewayProperties properties, ObjectMapper json) {
		this.client = builder.build();
		this.json = json;
		this.base = properties.baseUrl();
		this.token = properties.token();
	}

	private static SimpleClientHttpRequestFactory requests(Duration timeout) {
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(timeout);
		requests.setReadTimeout(timeout);
		return requests;
	}

	@Override
	public String send(String phoneE164, String code, Duration ttl) {
		JsonNode result = call("sendVerificationMessage",
			Map.of("phone_number", phoneE164, "code", code, "ttl", ttl.toSeconds()));
		JsonNode requestId = result.path("request_id");
		if (!requestId.isString() || requestId.asString().isBlank()) {
			throw new GatewayException("sendVerificationMessage answered without a request_id");
		}
		return requestId.asString();
	}

	@Override
	public void reportEntered(String requestId, String code) {
		call("checkVerificationStatus", Map.of("request_id", requestId, "code", code));
	}

	private JsonNode call(String method, Map<String, Object> body) {
		String answer;
		try {
			answer = client.post()
				.uri(base.resolve("/" + method))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				// Telegram answers errors as JSON with "ok": false, whatever the HTTP status.
				.onStatus(status -> true, (request, response) -> { })
				.body(String.class);
		} catch (RestClientException e) {
			// The exception text can quote the request; keep only its type.
			throw new GatewayException(method + " failed: " + e.getClass().getSimpleName());
		}
		JsonNode root;
		try {
			root = json.readTree(answer == null ? "" : answer);
		} catch (JacksonException e) {
			throw new GatewayException(method + " answer is not JSON");
		}
		if (!root.path("ok").asBoolean(false)) {
			JsonNode error = root.path("error");
			// Telegram's error codes (for example PHONE_NUMBER_INVALID) name no secret.
			throw new GatewayException(method + " refused: " + (error.isString() ? error.asString() : "unknown"));
		}
		return root.path("result");
	}

}
