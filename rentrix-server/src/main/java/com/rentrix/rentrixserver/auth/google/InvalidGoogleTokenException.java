package com.rentrix.rentrixserver.auth.google;

/** A Google ID token that failed verification; the API answers {@code 401 google_token_invalid}. */
public class InvalidGoogleTokenException extends RuntimeException {
	
	public InvalidGoogleTokenException(String message) {
		super(message);
	}
	
	public InvalidGoogleTokenException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
