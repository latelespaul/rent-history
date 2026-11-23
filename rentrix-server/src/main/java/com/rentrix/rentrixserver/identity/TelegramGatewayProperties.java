package com.rentrix.rentrixserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

/**
 * Telegram Gateway phone codes, from env only. {@code TELEGRAM_GATEWAY_TOKEN} is the Gateway API token (a secret:
 * never committed and never logged). Without it the "Telegram" phone method is hidden; with it, Telegram is the
 * default way to get the phone code and SMS is the fallback. {@code TELEGRAM_GATEWAY_FAKE=true} (dev and test only)
 * uses {@link FakeTelegramGateway} and never calls Telegram.
 *
 * @param baseUrl the API root, {@code https://gatewayapi.telegram.org} by default
 * @param timeout connect and read timeout of each call (default 5 s)
 */
@ConfigurationProperties(prefix = "app.telegram-gateway")
public record TelegramGatewayProperties(String token, URI baseUrl, boolean fake, Duration timeout) {

	public static final URI DEFAULT_BASE_URL = URI.create("https://gatewayapi.telegram.org");

	public TelegramGatewayProperties {
		token = StringUtils.hasText(token) ? token.trim() : null;
		baseUrl = baseUrl == null || !StringUtils.hasText(baseUrl.toString()) ? DEFAULT_BASE_URL : baseUrl;
		timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(5) : timeout;
	}

	@Override
	public String toString() {
		return "TelegramGatewayProperties[token=" + (token == null ? null : "***") + ", baseUrl=" + baseUrl
			+ ", fake=" + fake + ", timeout=" + timeout + "]";
	}

}
