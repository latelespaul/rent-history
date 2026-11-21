package com.rentrix.rentrixserver.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Allowed browser origins, from {@code CORS_ALLOWED_ORIGINS} (comma list). */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
	
	public CorsProperties {
		allowedOrigins = allowedOrigins == null
			? List.of()
			: allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
	
}
