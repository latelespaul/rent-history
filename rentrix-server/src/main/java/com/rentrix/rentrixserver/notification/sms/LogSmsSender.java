package com.rentrix.rentrixserver.notification.sms;

import lombok.extern.slf4j.Slf4j;

/**
 * Dev and test only ({@code SMS_BACKEND=log}; the startup guard refuses it in any other profile): writes the
 * message, which holds the OTP code, to the API log instead of sending it, so phone verification can be tried
 * locally without a provider. The phone number is masked; this line is the only place a code is ever logged.
 */
@Slf4j
public class LogSmsSender implements SmsSender {
	
	@Override
	public void send(String phoneE164, String text) {
		log.info("SMS to {} (SMS_BACKEND=log, not sent): {}", PhoneMasks.mask(phoneE164), text);
	}
	
}
