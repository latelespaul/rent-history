package com.rentrix.rentrixserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Rentrix API. Refuses to start unless SPRING_PROFILES_ACTIVE is dev, test or prod
 * (see {@link com.rentrix.rentrixserver.config.StartupGuard}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RentrixServerApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(RentrixServerApplication.class, args);
	}
	
}
