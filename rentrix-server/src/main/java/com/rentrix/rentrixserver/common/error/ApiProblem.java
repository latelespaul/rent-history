package com.rentrix.rentrixserver.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;

import java.util.List;
import java.util.Map;

/**
 * RFC 9457 Problem Details body used for every error response (D-60).
 * {@code code} is the stable machine code clients switch on and {@code title} is derived from it; {@code traceId} matches the
 * {@code X-Request-Id} response header and the log lines of the request. {@code approval} is set only on
 * {@code 403 approval_required}: {@code {role, status}} of the verification that blocks the write (D-91).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiProblem(
	String type,
	String title,
	int status,
	String code,
	String detail,
	String instance,
	List<FieldError> errors,
	String traceId,
	Map<String, String> approval
) {

	public static final String TYPE_BASE = "https://rentrix.in/problems/";

	public static ApiProblem of(HttpStatusCode status, String code, String detail, String instance,
		List<FieldError> errors, String traceId) {
		return of(status, code, detail, instance, errors, traceId, null);
	}

	public static ApiProblem of(HttpStatusCode status, String code, String detail, String instance,
		List<FieldError> errors, String traceId, Map<String, String> approval) {
		return new ApiProblem(TYPE_BASE + code.replace('_', '-'), title(code), status.value(), code, detail, instance,
			errors == null ? List.of() : errors, traceId, approval == null || approval.isEmpty() ? null : approval);
	}

	/** A short human title from the code, e.g. {@code review_window_closed} → "Review window closed". */
	static String title(String code) {
		String words = code.replace('_', ' ').trim();
		return words.isEmpty() ? "Error" : Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	/** One invalid input field. */
	public record FieldError(String field, String code, String message) {
	}

}
