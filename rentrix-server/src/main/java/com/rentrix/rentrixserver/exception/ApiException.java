package com.rentrix.rentrixserver.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
	
	private final HttpStatus status;
	
	public ApiException(String message, HttpStatus status) {
		super(message);
		this.status = status;
	}
	
	// Common shortcuts
	public static ApiException notFound(String message) {
		return new ApiException(message, HttpStatus.NOT_FOUND);
	}
	
	public static ApiException badRequest(String message) {
		return new ApiException(message, HttpStatus.BAD_REQUEST);
	}
	
	public static ApiException unauthorized(String message) {
		return new ApiException(message, HttpStatus.UNAUTHORIZED);
	}
	
	public static ApiException forbidden(String message) {
		return new ApiException(message, HttpStatus.FORBIDDEN);
	}
	
	public static ApiException conflict(String message) {
		return new ApiException(message, HttpStatus.CONFLICT);
	}
	
}