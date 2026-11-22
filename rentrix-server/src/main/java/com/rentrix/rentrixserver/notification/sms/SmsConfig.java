package com.rentrix.rentrixserver.notification.sms;

import com.rentrix.rentrixserver.identity.IdentityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Picks the {@link SmsSender} from {@code SMS_BACKEND} (D-101, D-137). No bean when it is empty: the phone method is
 * then unavailable ({@code 503 phone_otp_unavailable}). The startup guard refuses {@code log} outside dev and test,
 * {@code http} without its URL and key, and {@code admin-relay} under {@code prod} without {@code PREVIEW_MODE}.
 */
@Configuration(proxyBeanMethods = false)
public class SmsConfig {
	
	@Bean
	@ConditionalOnProperty(prefix = "app.sms", name = "backend", havingValue = SmsProperties.LOG)
	SmsSender logSmsSender() {
		return new LogSmsSender();
	}
	
	@Bean
	@ConditionalOnProperty(prefix = "app.sms", name = "backend", havingValue = SmsProperties.HTTP)
	SmsSender httpSmsSender(SmsProperties properties) {
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(properties.http().timeout());
		requests.setReadTimeout(properties.http().timeout());
		return new HttpSmsSender(RestClient.builder().requestFactory(requests), properties.http());
	}
	
	@Bean
	@ConditionalOnProperty(prefix = "app.sms", name = "backend", havingValue = SmsProperties.ADMIN_RELAY)
	AdminRelaySmsSender adminRelaySmsSender(IdentityProperties identity, Clock clock, Environment env) {
		return new AdminRelaySmsSender(identity.otpTtl(), clock, env.acceptsProfiles(Profiles.of("dev")));
	}
	
}
