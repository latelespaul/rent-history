package com.rentrix.rentrixserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	// This pulls the URL you just defined in your application-prod.yaml
	// If it's missing, it defaults to '*' (or you can specify a local fallback)
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**") // Allows CORS for all endpoints in your app
				  .allowedOrigins(
					  "http://localhost:5173",
					  "https://rentrix-sage.vercel.app",
					  "https://myrentrix.vercel.app",
					  "http://localhost:8080/swagger-ui"
				  )
				  .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				  .allowedHeaders("*")
				  .allowCredentials(true);
	}
	
}