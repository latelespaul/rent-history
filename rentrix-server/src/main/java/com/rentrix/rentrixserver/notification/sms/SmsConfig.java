package com.rentrix.rentrixserver.notification.sms;

import com.rentrix.rentrixserver.config.BrandProperties;
import com.rentrix.rentrixserver.identity.IdentityProperties;
import com.rentrix.rentrixserver.identity.PhoneOtpChallengeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Picks the {@link SmsSender} from {@code SMS_BACKEND} (D-101, D-137). No bean when it is empty: the phone method is
 * then unavailable ({@code 503 phone_otp_unavailable}). The startup guard refuses {@code log} outside dev and test,
 * {@code http} without its URL and key, and {@code admin-relay} under {@code prod} without {@code PREVIEW_MODE}.
 * With {@code admin-relay} and both Telegram settings, each held code is also pushed to Telegram.
 */
@Slf4j
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
	AdminRelaySmsSender adminRelaySmsSender(IdentityProperties identity, Clock clock, Environment env,
		SmsProperties properties, BrandProperties brand, PhoneOtpChallengeRepository challenges,
		PlatformTransactionManager transactions) {
		RelayPush push = RelayPush.NONE;
		if (properties.telegram().enabled()) {
			push = telegramPush(properties.telegram(), brand, challenges, transactions, clock);
			log.info("admin-relay codes are also pushed to {} Telegram chat(s)", properties.telegram().chatIds().size());
		}
		return new AdminRelaySmsSender(identity.otpTtl(), clock, env.acceptsProfiles(Profiles.of("dev")), push,
			identity::isTestNumber);
	}
	
	private static RelayPush telegramPush(SmsProperties.Telegram telegram, BrandProperties brand,
		PhoneOtpChallengeRepository challenges, PlatformTransactionManager transactions, Clock clock) {
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(Duration.ofSeconds(5));
		requests.setReadTimeout(Duration.ofSeconds(5));
		TransactionTemplate readOnly = new TransactionTemplate(transactions);
		readOnly.setReadOnly(true);
		// The challenge row is committed before the sender runs, so its user is known here.
		Function<String, String> nameForPhone = phone -> readOnly.execute(
			status -> challenges.findFirstByPhoneE164OrderByCreatedAtDesc(phone)
				.map(challenge -> challenge.getUser().getDisplayName()).orElse(null));
		Executor executor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "telegram-relay-push");
			thread.setDaemon(true);
			return thread;
		});
		return new TelegramRelayPush(RestClient.builder().requestFactory(requests), telegram, brand.name(),
			nameForPhone, clock, executor);
	}
	
}
