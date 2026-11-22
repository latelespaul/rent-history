package com.rentrix.rentrixserver.notification.sms;

/** The SMS provider refused or did not answer. The message says why without the recipient or the text. */
public class SmsSendException extends RuntimeException {
	
	public SmsSendException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
