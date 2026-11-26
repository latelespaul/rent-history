package com.rentrix.rentrixserver.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Mail settings (§17, D-172), from env only. Mail is on when {@code enabled} ({@code MAIL_ENABLED}, default
 * {@code true}; {@code false} in dev and test) and {@code RESEND_API_KEY} is set; without the key the API starts,
 * logs that mail is off and only writes emails to the log ({@link LogEmailSender}).
 *
 * @param enabled      the switch; {@code false} keeps mail off even with a key
 * @param resendApiKey the Resend API key; secret, env only, never logged
 * @param from         the sender, default {@code Doorway Living <hello@doorwayliving.in>}
 * @param apiBaseUrl   public URL of this API with its {@code /api/v1} path ({@code APP_API_BASE_URL}), for the
 *                     unsubscribe link; empty = {@code APP_BASE_URL} + {@code /api/v1}, where LIVE and DEV serve
 *                     the API
 * @param sendInterval pause between two emails of an admin reminder, under Resend's per-second limit (default
 *                     600 ms)
 * @param timeout      connect and read timeout of a Resend call (default 10 s)
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(Boolean enabled, String resendApiKey, String from, String apiBaseUrl,
	Duration sendInterval, Duration timeout) {
	
	public static final String DEFAULT_FROM = "Doorway Living <hello@doorwayliving.in>";
	
	public MailProperties {
		enabled = enabled == null || enabled;
		resendApiKey = StringUtils.hasText(resendApiKey) ? resendApiKey.trim() : null;
		from = StringUtils.hasText(from) ? from.trim() : DEFAULT_FROM;
		apiBaseUrl = StringUtils.hasText(apiBaseUrl) ? stripSlash(apiBaseUrl.trim()) : null;
		sendInterval = sendInterval == null ? Duration.ofMillis(600) : sendInterval;
		timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
	}
	
	static String stripSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
	
	@Override
	public String toString() {
		return "MailProperties[enabled=" + enabled + ", resendApiKey=" + (resendApiKey == null ? null : "***")
			+ ", from=" + from + ", apiBaseUrl=" + apiBaseUrl + ", sendInterval=" + sendInterval + ", timeout="
			+ timeout + "]";
	}
	
}
