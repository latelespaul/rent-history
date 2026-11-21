package com.rentrix.rentrixserver.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

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
	
	public ApiException(HttpStatus status, String code, String detail) {
		this(status, code, detail, List.of());
	}
	
	public ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors) {
		super(detail);
		this.status = status;
		this.code = code;
		this.errors = List.copyOf(errors);
	}
	
	public static ApiException notFound(String what) {
		return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found.");
	}
	
	public static ApiException validation(List<ApiProblem.FieldError> errors) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "validation_failed", "The request has invalid fields.",
			errors);
	}
	
}
