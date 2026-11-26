package com.rentrix.rentrixserver.notification.email;

/**
 * Sends email (D-65, D-172): tenancy invites, confirm requests and outcomes, rent records to confirm, review and
 * verification notices, the welcome email and admin reminders. {@link MailConfig} picks {@link ResendEmailSender}
 * when mail is configured, else {@link LogEmailSender}.
 */
public interface EmailSender {
	
	/**
	 * Fire and forget, for emails that go with a user action: a real sender sends after the current transaction
	 * commits, off the request thread, and only logs a failure, so mail never slows down or fails the action.
	 */
	void send(EmailMessage message);
	
	/**
	 * Sends now, on the caller's thread, for senders that must know the outcome (the admin reminder records only
	 * delivered emails). Call it outside a database transaction.
	 *
	 * @throws EmailSendException when the provider refused the email or could not be reached
	 */
	default void sendNow(EmailMessage message) {
		send(message);
	}
	
	/** Whether emails really leave the server; {@code false} for {@link LogEmailSender}. */
	default boolean delivers() {
		return false;
	}
	
}
