package com.rentrix.rentrixserver.common.error;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;

/**
 * A business or request error with a stable machine {@code code} (D-60). Thrown by services;
 * {@link GlobalExceptionHandler} turns it into {@code application/problem+json}.
 */
@Getter
public class ApiException extends RuntimeException {
	
	private final HttpStatus status;
	private final String code;
	private final List<ApiProblem.FieldError> errors;
	/** Extra response headers, e.g. {@code Retry-After} on 429. */
	private final HttpHeaders headers;
	
	public ApiException(HttpStatus status, String code, String detail) {
		this(status, code, detail, List.of());
	}
	
	public ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors) {
		this(status, code, detail, errors, HttpHeaders.EMPTY);
	}
	
	private ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors,
		HttpHeaders headers) {
		super(detail);
		this.status = status;
		this.code = code;
		this.errors = List.copyOf(errors);
		this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
	}
	
	public static ApiException notFound(String what) {
		return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found.");
	}
	
	/** 409: duplicate, overlap or a state transition that is not allowed (D-60). */
	public static ApiException conflict(String code, String detail) {
		return new ApiException(HttpStatus.CONFLICT, code, detail);
	}
	
	/** 422 for a broken business rule, e.g. {@code review_window_closed}. */
	public static ApiException rule(String code, String detail) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, detail);
	}
	
	/** 429 with {@code Retry-After} in whole seconds (at least 1). */
	public static ApiException rateLimited(Duration retryAfter) {
		HttpHeaders headers = new HttpHeaders();
		long seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
		headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many requests. Retry later.",
			List.of(), headers);
	}
	
	public static ApiException validation(List<ApiProblem.FieldError> errors) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "validation_failed", "The request has invalid fields.",
			errors);
	}
	
}
