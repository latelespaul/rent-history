package com.rentrix.rentrixserver.notification.email;

/**
 * Sends transactional email (D-65): tenancy invites, confirm requests and outcomes, rent records to confirm and,
 * later, review and verification notices. v1 ships only {@link LogEmailSender}; an SMTP implementation
 * ({@code MAIL_*} env) is added before real users are invited.
 */
public interface EmailSender {
	
	void send(EmailMessage message);
	
}
