package com.rentrix.rentrixserver.notification.sms;

/**
 * Sends one text message (§11). SMS is used for the phone OTP code only (D-101). The bean exists only when
 * {@code SMS_BACKEND} is set: {@code log} ({@link LogSmsSender}, dev and test) or {@code http}
 * ({@link HttpSmsSender}, a provider in prod). Without it the phone method is unavailable.
 */
public interface SmsSender {
	
	/**
	 * @param phoneE164 the recipient, E.164
	 * @param text      the message; implementations never log it outside dev and test
	 * @throws SmsSendException when the message could not be handed to the provider
	 */
	void send(String phoneE164, String text);
	
}
