package com.rentrix.rentrixserver.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Sign-in settings (§13, §17). {@code google.clientId} comes from {@code GOOGLE_CLIENT_ID} only and has no
 * committed default (D-74). Who may use the admin console is {@code AdminConsoleProperties} (§13.3).
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Google google, Duration refreshTokenTtl, Duration refreshReuseGrace,
	RateLimit rateLimit) {
	
	public AuthProperties {
		google = google == null ? new Google(null) : google;
		refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
		refreshReuseGrace = refreshReuseGrace == null ? Duration.ofSeconds(30) : refreshReuseGrace;
		rateLimit = rateLimit == null ? new RateLimit(0, null) : rateLimit;
	}
	
	/** Google sign-in is offered only when a client id is configured. */
	public boolean googleEnabled() {
		return google.clientId() != null;
	}
	
	/** {@code clientId} is null when {@code GOOGLE_CLIENT_ID} is unset or blank. */
	public record Google(String clientId) {
		
		public Google {
			clientId = StringUtils.hasText(clientId) ? clientId.trim() : null;
		}
		
	}
	
	/** Requests allowed per {@code period} and client IP on the sign-in endpoints (default 20 per 15 min). */
	public record RateLimit(int requests, Duration period) {
		
		public RateLimit {
			requests = requests <= 0 ? 20 : requests;
			period = period == null ? Duration.ofMinutes(15) : period;
		}
		
	}
	
}
