package com.rentrix.rentrixserver.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;

import java.util.List;

/**
 * RFC 9457 Problem Details body used for every error response (D-60).
 * {@code code} is the stable machine code clients switch on and {@code title} is derived from it; {@code traceId} matches the
 * {@code X-Request-Id} response header and the log lines of the request.
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
	String traceId
) {

	public static final String TYPE_BASE = "https://rentrix.in/problems/";

	public static ApiProblem of(HttpStatusCode status, String code, String detail, String instance,
		List<FieldError> errors, String traceId) {
		return new ApiProblem(TYPE_BASE + code.replace('_', '-'), title(code), status.value(), code, detail, instance,
			errors == null ? List.of() : errors, traceId);
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
