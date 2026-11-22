package com.rentrix.rentrixserver.notification.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Sends each relayed OTP code to the admins' Telegram chats ({@code ADMIN_RELAY_TELEGRAM_BOT_TOKEN} and
 * {@code ADMIN_RELAY_TELEGRAM_CHAT_IDS}, D-137): {@code "<brand> OTP for <name> (+91 XXXXXX3210): 123456 — expires 10
 * min"}. The send runs on {@code executor}, so a slow or failing Telegram never delays or breaks the OTP. The bot
 * token is part of the request URL, so a failure logs only the exception type, never its text.
 */
@Slf4j
public class TelegramRelayPush implements RelayPush {
	
	private final RestClient client;
	private final URI sendMessage;
	private final List<String> chatIds;
	private final String brand;
	private final Function<String, String> nameForPhone;
	private final Clock clock;
	private final Executor executor;
	
	/**
	 * @param nameForPhone the display name of the user who asked for a code for this number, or null
	 */
	public TelegramRelayPush(RestClient.Builder builder, SmsProperties.Telegram telegram, String brand,
		Function<String, String> nameForPhone, Clock clock, Executor executor) {
		this.client = builder.build();
		// Built once, not through a URI template: a template would encode the ":" in the token.
		this.sendMessage = URI.create("https://api.telegram.org/bot" + telegram.botToken() + "/sendMessage");
		this.chatIds = telegram.chatIds();
		this.brand = brand;
		this.nameForPhone = nameForPhone;
		this.clock = clock;
		this.executor = executor;
	}
	
	@Override
	public void push(String phoneE164, String code, Instant expiresAt) {
		executor.execute(() -> send(phoneE164, code, expiresAt));
	}
	
	private void send(String phoneE164, String code, Instant expiresAt) {
		String text;
		try {
			text = message(phoneE164, code, expiresAt);
		} catch (RuntimeException e) {
			log.warn("Telegram relay push skipped for {} ({})", PhoneMasks.mask(phoneE164), e.getClass().getSimpleName());
			return;
		}
		for (String chatId : chatIds) {
			Map<String, String> body = new LinkedHashMap<>();
			body.put("chat_id", chatId);
			body.put("text", text);
			try {
				client.post().uri(sendMessage).contentType(MediaType.APPLICATION_JSON).body(body)
					.retrieve().toBodilessEntity();
			} catch (RestClientException e) {
				// The exception text can carry the request URL, which holds the bot token: keep only its type.
				log.warn("Telegram relay push failed for {} ({})", PhoneMasks.mask(phoneE164),
					e.getClass().getSimpleName());
			}
		}
	}
	
	private String message(String phoneE164, String code, Instant expiresAt) {
		String name = nameForPhone.apply(phoneE164);
		long minutes = Math.max(0, (Duration.between(clock.instant(), expiresAt).toSeconds() + 59) / 60);
		return brand + " OTP for " + (name == null || name.isBlank() ? "a user" : name) + " ("
			+ PhoneMasks.mask(phoneE164) + "): " + code + " — expires " + minutes + " min";
	}
	
}
