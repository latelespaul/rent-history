package com.rentrix.rentrixserver.common.error;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
	/** {@code {role, status}} on {@code 403 approval_required} (D-91); empty otherwise. */
	private final Map<String, String> approval;

	public ApiException(HttpStatus status, String code, String detail) {
		this(status, code, detail, List.of());
	}

	public ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors) {
		this(status, code, detail, errors, HttpHeaders.EMPTY);
	}

	private ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors,
		HttpHeaders headers) {
		this(status, code, detail, errors, headers, Map.of());
	}

	private ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors,
		HttpHeaders headers, Map<String, String> approval) {
		super(detail);
		this.status = status;
		this.code = code;
		this.errors = List.copyOf(errors);
		this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
		this.approval = Map.copyOf(approval);
	}

	/**
	 * 403 {@code approval_required} (D-91): the caller's verification for {@code role} is not {@code APPROVED}.
	 * The body carries {@code approval: {role, status}} so the web can show the matching "pending approval" state.
	 */
	public static ApiException approvalRequired(String role, String status) {
		return new ApiException(HttpStatus.FORBIDDEN, "approval_required",
			"Your " + role.toLowerCase(Locale.ROOT) + " account must be approved before you can do this.",
			List.of(), HttpHeaders.EMPTY, Map.of("role", role, "status", status));
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
