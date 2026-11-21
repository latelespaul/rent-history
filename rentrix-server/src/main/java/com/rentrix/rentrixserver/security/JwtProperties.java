package com.rentrix.rentrixserver.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Access-token settings (D-62). {@code secret} comes from {@code JWT_SECRET}; the startup
 * guard refuses to start when it is shorter than 32 bytes.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) {
	
	public JwtProperties {
		if (accessTokenTtl == null) {
			accessTokenTtl = Duration.ofMinutes(15);
		}
	}
	
}
