package com.rentrix.rentrixserver.identity;

import java.util.Optional;

/** The configured {@link TelegramGateway}, if any (see {@link TelegramGatewayConfig}). */
public record TelegramGatewayHolder(TelegramGateway gateway) {

	public Optional<TelegramGateway> get() {
		return Optional.ofNullable(gateway);
	}

}
