package com.rentrix.rentrixserver.identity;

import java.time.Duration;

/**
 * Sends a phone code through the Telegram Gateway API (gatewayapi.telegram.org): Telegram shows the code in the
 * user's Telegram app, on the account registered to that number. We generate the code and keep only its HMAC, as
 * for SMS (D-101); Telegram is the delivery channel. After a correct entry we report the code back so Telegram can
 * track delivery. {@link HttpTelegramGateway} needs {@code TELEGRAM_GATEWAY_TOKEN}; dev and test use
 * {@link FakeTelegramGateway}. No webhook and no {@code getUpdates}: nothing calls us back.
 */
public interface TelegramGateway {

	/**
	 * {@code sendVerificationMessage} with our {@code code} to {@code phoneE164}.
	 *
	 * @return Telegram's {@code request_id} for the message
	 * @throws GatewayException when Telegram refuses or cannot be reached
	 */
	String send(String phoneE164, String code, Duration ttl);

	/**
	 * {@code checkVerificationStatus} with the code the user entered. Best effort: the code was already checked
	 * against our HMAC, so a failure here never undoes the verification.
	 */
	void reportEntered(String requestId, String code);

	/** Telegram refused or could not be reached. The message never holds the token, the number or the code. */
	class GatewayException extends RuntimeException {

		public GatewayException(String message) {
			super(message);
		}

	}

}
