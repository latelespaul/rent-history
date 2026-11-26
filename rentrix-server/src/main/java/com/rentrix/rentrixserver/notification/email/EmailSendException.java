package com.rentrix.rentrixserver.notification.email;

/** An email was not sent. The message never holds the recipient, the text or the provider key. */
public class EmailSendException extends RuntimeException {
	
	public EmailSendException(String message) {
		super(message);
	}
	
}
