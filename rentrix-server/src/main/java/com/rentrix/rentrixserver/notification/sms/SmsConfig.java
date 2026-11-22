package com.rentrix.rentrixserver.notification.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Picks the {@link SmsSender} from {@code SMS_BACKEND} (D-101). No bean when it is empty: the phone method is then
 * unavailable ({@code 503 phone_otp_unavailable}). The startup guard refuses {@code log} outside dev and test and
 * {@code http} without its URL and key.
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
	
}
