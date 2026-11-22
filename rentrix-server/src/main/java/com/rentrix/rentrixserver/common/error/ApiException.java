package com.rentrix.rentrixserver.common.error;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
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
	/** {@code {role, status}} of a verification that blocks a write; unused since open by default (D-132). */
	private final Map<String, String> approval;
	/**
	 * The steps still to do: on {@code 422 verification_incomplete} (D-105) and {@code 403 id_verification_required}
	 * ({@code phone}, D-117); empty otherwise.
	 */
	private final List<String> missing;
	/** The clashing tenancy's period on {@code 409 tenancy_overlap}; empty otherwise. */
	private final Map<String, String> conflict;

	public ApiException(HttpStatus status, String code, String detail) {
		this(status, code, detail, List.of());
	}

	public ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors) {
		this(status, code, detail, errors, HttpHeaders.EMPTY);
	}

	private ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors,
		HttpHeaders headers) {
		this(status, code, detail, errors, headers, Map.of(), List.of(), Map.of());
	}

	private ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors,
		HttpHeaders headers, Map<String, String> approval, List<String> missing) {
		this(status, code, detail, errors, headers, approval, missing, Map.of());
	}

	private ApiException(HttpStatus status, String code, String detail, List<ApiProblem.FieldError> errors,
		HttpHeaders headers, Map<String, String> approval, List<String> missing, Map<String, String> conflict) {
		super(detail);
		this.status = status;
		this.code = code;
		this.errors = List.copyOf(errors);
		this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
		this.approval = Map.copyOf(approval);
		this.missing = List.copyOf(missing);
		this.conflict = Map.copyOf(conflict);
	}

	/**
	 * 403 {@code id_verification_required} (D-117, D-132): posting needs a verified phone, and the caller has none
	 * (never verified, or revoked by a phone change). The body names it in {@code missing: ["phone"]}; verifying a
	 * phone lifts it at once, with no admin step.
	 */
	public static ApiException idVerificationRequired() {
		return new ApiException(HttpStatus.FORBIDDEN, "id_verification_required",
			"Verify your phone number to continue.", List.of(), HttpHeaders.EMPTY, Map.of(), List.of("phone"));
	}

	/** 422 {@code verification_incomplete}: submit with steps still missing, listed in {@code missing} (D-105). */
	public static ApiException verificationIncomplete(List<String> missing) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "verification_incomplete",
			"Finish these steps before you submit: " + String.join(", ", missing) + ".", List.of(), HttpHeaders.EMPTY,
			Map.of(), missing);
	}

	public static ApiException notFound(String what) {
		return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found.");
	}

	/** 409: duplicate, overlap or a state transition that is not allowed (D-60). */
	public static ApiException conflict(String code, String detail) {
		return new ApiException(HttpStatus.CONFLICT, code, detail);
	}

	/**
	 * 409 {@code tenancy_overlap}: the dates clash with a tenancy already on the home. {@code conflict} carries that
	 * tenancy's period ({@code from}, {@code to}) and, for the home's landlord only, its {@code tenancyId}.
	 */
	public static ApiException tenancyOverlap(String detail, Map<String, String> conflict) {
		return new ApiException(HttpStatus.CONFLICT, "tenancy_overlap", detail, List.of(), HttpHeaders.EMPTY, Map.of(),
			List.of(), conflict);
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
