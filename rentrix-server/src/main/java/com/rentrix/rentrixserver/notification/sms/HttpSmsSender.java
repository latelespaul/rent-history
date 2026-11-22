package com.rentrix.rentrixserver.notification.sms;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends through an HTTP SMS provider ({@code SMS_BACKEND=http}): {@code POST SMS_HTTP_URL} with JSON
 * {@code {to, sender, message}} and {@code Authorization: Bearer SMS_API_KEY}. Nothing about the message,
 * the number or the key is logged. A provider with another request shape gets its own {@link SmsSender}.
 */
public class HttpSmsSender implements SmsSender {
	
	private final RestClient client;
	private final String senderId;
	
	public HttpSmsSender(RestClient.Builder builder, SmsProperties.Http http) {
		this.client = builder.baseUrl(http.url())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + http.apiKey())
			.build();
		this.senderId = http.senderId();
	}
	
	@Override
	public void send(String phoneE164, String text) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("to", phoneE164);
		if (senderId != null) {
			body.put("sender", senderId);
		}
		body.put("message", text);
		try {
			client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			// The exception text can echo the request; keep only its type.
			throw new SmsSendException("SMS provider call failed (" + e.getClass().getSimpleName() + ")", null);
		}
	}
	
}
