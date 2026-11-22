package com.rentrix.rentrixserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * The product name users see in emails, SMS, API messages and the API docs ({@code app.brand.name}).
 * Every user-visible brand string on the server comes from here, so a rename is one config change.
 */
@ConfigurationProperties(prefix = "app.brand")
public record BrandProperties(String name) {
	
	public static final String DEFAULT_NAME = "Doorway Living";
	
	public BrandProperties {
		name = StringUtils.hasText(name) ? name.trim() : DEFAULT_NAME;
	}
	
}
