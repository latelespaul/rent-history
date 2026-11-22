package com.rentrix.rentrixserver.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

/**
 * The real Truecaller profile fetch: {@code GET <endpoint>} with {@code Authorization: Bearer <accessToken>} and
 * {@code Cache-Control: no-cache}; the answer's {@code phoneNumbers[0]} is the verified number. Used unless
 * {@code TRUECALLER_FAKE=true} (dev and test only).
 */
@Component
@ConditionalOnProperty(prefix = "app.truecaller", name = "fake", havingValue = "false", matchIfMissing = true)
public class TruecallerVerifier implements PhoneVerifier {

	private final RestClient client;
	private final ObjectMapper json;

	public TruecallerVerifier(TruecallerProperties properties, ObjectMapper json) {
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(properties.timeout());
		requests.setReadTimeout(properties.timeout());
		this.client = RestClient.builder().requestFactory(requests).build();
		this.json = json;
	}

	@Override
	public String fetchPhone(URI endpoint, String accessToken) {
		String body;
		try {
			body = client.get()
				.uri(endpoint)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header(HttpHeaders.CACHE_CONTROL, "no-cache")
				.retrieve()
				.body(String.class);
		} catch (RestClientException e) {
			// The exception text can quote the request; keep only its type.
			throw new VerifierException("profile fetch failed: " + e.getClass().getSimpleName());
		}
		return firstPhone(body);
	}

	String firstPhone(String body) {
		JsonNode first;
		try {
			first = json.readTree(body == null ? "" : body).path("phoneNumbers").path(0);
		} catch (JacksonException e) {
			throw new VerifierException("profile is not JSON");
		}
		if (first.isNumber()) {
			return first.numberValue().toString();
		}
		if (first.isString() && !first.asString().isBlank()) {
			return first.asString();
		}
		throw new VerifierException("profile has no phone number");
	}

}
