package com.rentrix.rentrixserver.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
	private String message;
	private int status;
	private Instant timestamp;
	private Map<String, String> fieldErrors; // nullable; only for validation errors
	
	public ErrorResponse(String message, int status) {
		this(message, status, Instant.now(), null);
	}
	
}