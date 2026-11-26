package com.rentrix.rentrixserver.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Sends through the Resend HTTP API (D-172): {@code POST https://api.resend.com/emails} with
 * {@code Authorization: Bearer RESEND_API_KEY} and JSON {@code {from, to, subject, text, html, headers}}. An email
 * with an unsubscribe link gets {@code List-Unsubscribe} and {@code List-Unsubscribe-Post} (one-click, RFC 8058).
 * Never logs the key, the text or the full recipient.
 */
@Slf4j
public class ResendEmailSender implements EmailSender {
	
	static final String URL = "https://api.resend.com/emails";
	
	private final RestClient client;
	private final String from;
	private final Executor executor;
	
	public ResendEmailSender(RestClient.Builder builder, MailProperties properties, Executor executor) {
		this.client = builder.baseUrl(URL)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.resendApiKey())
			.build();
		this.from = properties.from();
		this.executor = executor;
	}
	
	@Override
	public void send(EmailMessage message) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			// After commit: a rolled-back action sends nothing, and the HTTP call never holds a database lock.
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					executor.execute(() -> sendQuietly(message));
				}
			});
		} else {
			executor.execute(() -> sendQuietly(message));
		}
	}
	
	@Override
	public void sendNow(EmailMessage message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", from);
		body.put("to", List.of(message.to()));
		body.put("subject", message.subject());
		body.put("text", message.body());
		if (message.html() != null) {
			body.put("html", message.html());
		}
		if (message.unsubscribeUrl() != null) {
			body.put("headers", Map.of("List-Unsubscribe", "<" + message.unsubscribeUrl() + ">",
				"List-Unsubscribe-Post", "List-Unsubscribe=One-Click"));
		}
		try {
			client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
		} catch (RestClientResponseException e) {
			// The response body can echo the request; keep only the status.
			throw new EmailSendException("Resend refused the email (HTTP " + e.getStatusCode().value() + ")");
		} catch (RestClientException e) {
			throw new EmailSendException("Resend call failed (" + e.getClass().getSimpleName() + ")");
		}
	}
	
	@Override
	public boolean delivers() {
		return true;
	}
	
	private void sendQuietly(EmailMessage message) {
		try {
			sendNow(message);
		} catch (RuntimeException e) {
			log.warn("Email to {} not sent: {}", LogEmailSender.mask(message.to()),
				e instanceof EmailSendException ? e.getMessage() : e.getClass().getSimpleName());
		}
	}
	
}
