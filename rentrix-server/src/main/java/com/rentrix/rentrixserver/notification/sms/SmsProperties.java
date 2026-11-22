package com.rentrix.rentrixserver.notification.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * SMS settings (§17, D-101), from env only: {@code SMS_BACKEND} ({@code log}, {@code http} or the preview-only
 * {@code admin-relay}, D-137; empty = no SMS)
 * and, for {@code http}, {@code SMS_HTTP_URL}, {@code SMS_API_KEY} and {@code SMS_SENDER_ID}. No provider
 * credentials are committed anywhere.
 */
@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(String backend, Http http) {
	
	public static final String LOG = "log";
	public static final String HTTP = "http";
	/** Preview only (D-137): codes are held for admins, nothing is sent. */
	public static final String ADMIN_RELAY = "admin-relay";
	
	public SmsProperties {
		backend = StringUtils.hasText(backend) ? backend.trim().toLowerCase(Locale.ROOT) : null;
		http = http == null ? new Http(null, null, null, null) : http;
	}
	
	/**
	 * @param url      the provider endpoint that accepts {@code POST {to, sender, message}}
	 * @param apiKey   sent as {@code Authorization: Bearer}; secret, env only
	 * @param senderId the registered sender id (DLT header in India)
	 * @param timeout  connect and read timeout (default 10 s)
	 */
	public record Http(String url, String apiKey, String senderId, Duration timeout) {
		
		public Http {
			url = StringUtils.hasText(url) ? url.trim() : null;
			apiKey = StringUtils.hasText(apiKey) ? apiKey.trim() : null;
			senderId = StringUtils.hasText(senderId) ? senderId.trim() : null;
			timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
		}
		
		@Override
		public String toString() {
			return "Http[url=" + url + ", apiKey=" + (apiKey == null ? null : "***") + ", senderId=" + senderId
				+ ", timeout=" + timeout + "]";
		}
		
	}
	
}
