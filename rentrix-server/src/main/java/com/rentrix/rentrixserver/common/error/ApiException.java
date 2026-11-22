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
	/** The verification steps still to do, on {@code 422 verification_incomplete} (D-105); empty otherwise. */
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
	 * 403 {@code approval_required} (D-91): the caller's verification for {@code role} is not {@code APPROVED}.
	 * The body carries {@code approval: {role, status}} so the web can show the matching "pending approval" state.
	 */
	public static ApiException approvalRequired(String role, String status) {
		return new ApiException(HttpStatus.FORBIDDEN, "approval_required",
			"Your " + role.toLowerCase(Locale.ROOT) + " account must be approved before you can do this.",
			List.of(), HttpHeaders.EMPTY, Map.of("role", role, "status", status), List.of());
	}

	/**
	 * 403 {@code id_verification_required} (D-105): the role is approved, but no ID method (Aadhaar, PAN or phone)
	 * is verified any more, e.g. after the only one was revoked. Verifying another method lifts it; no re-approval.
	 */
	public static ApiException idVerificationRequired() {
		return new ApiException(HttpStatus.FORBIDDEN, "id_verification_required",
			"Verify your ID again (Aadhaar, PAN or phone) before you can do this.");
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
