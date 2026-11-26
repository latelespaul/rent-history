package com.rentrix.rentrixserver.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Picks the {@link EmailSender} (D-172): {@link ResendEmailSender} when {@code MAIL_ENABLED} is on,
 * {@code RESEND_API_KEY} is set and {@code APP_BASE_URL} (the links in emails) is known; otherwise {@link LogEmailSender}, with one startup line that says why mail is off. The API always starts.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class MailConfig {
	
	@Bean
	EmailSender emailSender(MailProperties properties, @Value("${app.web.base-url:}") String webBaseUrl) {
		return choose(properties, webBaseUrl, RestClient.builder().requestFactory(requests(properties)), executor());
	}
	
	static EmailSender choose(MailProperties properties, String webBaseUrl, RestClient.Builder builder,
		Executor executor) {
		if (!properties.enabled()) {
			log.info("Mail is off (MAIL_ENABLED=false): emails are written to the log, none is sent");
			return new LogEmailSender();
		}
		if (properties.resendApiKey() == null) {
			log.info("Mail is off (no RESEND_API_KEY): emails are written to the log, none is sent");
			return new LogEmailSender();
		}
		if (!StringUtils.hasText(webBaseUrl)) {
			log.warn("Mail is off: RESEND_API_KEY is set, but APP_BASE_URL is needed for the links in emails; "
				+ "emails are written to the log, none is sent");
			return new LogEmailSender();
		}
		log.info("Mail is on: Resend, from {}", properties.from());
		return new ResendEmailSender(builder, properties, executor);
	}
	
	private static SimpleClientHttpRequestFactory requests(MailProperties properties) {
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(properties.timeout());
		requests.setReadTimeout(properties.timeout());
		return requests;
	}
	
	/** One daemon thread: emails that go with a user action are sent one by one, off the request thread. */
	private static Executor executor() {
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "mail-sender");
			thread.setDaemon(true);
			return thread;
		});
	}
	
}
