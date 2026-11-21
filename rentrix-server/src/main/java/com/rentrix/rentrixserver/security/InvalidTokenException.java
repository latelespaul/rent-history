package com.rentrix.rentrixserver.security;

/** An access token that is malformed, badly signed or expired. */
public class InvalidTokenException extends RuntimeException {
	
	public InvalidTokenException(String message, Throwable cause) {
		super(message, cause);
	}
	
}
