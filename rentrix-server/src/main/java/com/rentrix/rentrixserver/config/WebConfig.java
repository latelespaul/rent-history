package com.rentrix.rentrixserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	// This pulls the URL you just defined in your application-prod.yaml
	// If it's missing, it defaults to '*' (or you can specify a local fallback)
	@Value("${app.cors.allowed-origins:*}")
	private String allowedOrigins;
	
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**") // Allows CORS for all endpoints in your app
				  .allowedOrigins(allowedOrigins)
				  .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				  .allowedHeaders("*")
				  .allowCredentials(true);
	}
	
}