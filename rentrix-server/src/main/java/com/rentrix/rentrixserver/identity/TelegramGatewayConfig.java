package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.notification.sms.AdminRelaySmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Picks the {@link TelegramGateway}: the fake when {@code TELEGRAM_GATEWAY_FAKE=true} (dev and test only, the
 * startup guard refuses it elsewhere), the real client when {@code TELEGRAM_GATEWAY_TOKEN} is set, else none. With
 * no gateway the Telegram phone method is hidden.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class TelegramGatewayConfig {

	@Bean
	TelegramGatewayHolder telegramGateway(TelegramGatewayProperties properties, ObjectMapper json,
		ObjectProvider<AdminRelaySmsSender> relay) {
		if (properties.fake()) {
			log.info("Telegram phone codes use the fake gateway (TELEGRAM_GATEWAY_FAKE=true); nothing is sent");
			return new TelegramGatewayHolder(new FakeTelegramGateway(relay.getIfAvailable()));
		}
		if (properties.token() != null) {
			log.info("Telegram phone codes go through the Telegram Gateway API");
			return new TelegramGatewayHolder(new HttpTelegramGateway(properties, json));
		}
		return new TelegramGatewayHolder(null);
	}

}
