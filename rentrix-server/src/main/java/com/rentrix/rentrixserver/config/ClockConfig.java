package com.rentrix.rentrixserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** UTC clock for business time (D-14); tests can replace it. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {
	
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
	
}
