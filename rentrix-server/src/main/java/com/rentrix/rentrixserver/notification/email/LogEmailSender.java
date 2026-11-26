package com.rentrix.rentrixserver.notification.email;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * Writes email to the API log instead of sending it (D-65): the sender whenever mail is off ({@link MailConfig}).
 * The recipient is masked and the subject is logged at {@code INFO}; the body, which can hold a single-use invite
 * link, only at {@code DEBUG}, which the dev profile switches on for {@code com.rentrix} and prod leaves off.
 */
@Slf4j
public class LogEmailSender implements EmailSender {
	
	@Override
	public void send(EmailMessage message) {
		log.info("Email to {} (not sent, no mail provider): {}", mask(message.to()), message.subject());
		log.debug("Email body for {}:\n{}", mask(message.to()), message.body());
	}
	
	/** {@code asha.rao@example.test} → {@code a***@example.test}. */
	static String mask(String email) {
		if (email == null) {
			return null;
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return email.substring(0, 1).toLowerCase(Locale.ROOT) + "***" + email.substring(at);
	}
	
}
