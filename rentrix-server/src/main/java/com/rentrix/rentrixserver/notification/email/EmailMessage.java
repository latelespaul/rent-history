package com.rentrix.rentrixserver.notification.email;

/**
 * One email (D-65). {@code body} is plain text; {@code html} is an optional HTML version of the same text. A
 * non-transactional email (welcome, reminders, D-172) carries {@code unsubscribeUrl}, which also goes out as the
 * {@code List-Unsubscribe} header. Never holds document data, ID numbers or another user's data.
 */
public record EmailMessage(String to, String subject, String body, String html, String unsubscribeUrl) {
	
	/** A plain-text transactional email. */
	public EmailMessage(String to, String subject, String body) {
		this(to, subject, body, null, null);
	}
	
}
