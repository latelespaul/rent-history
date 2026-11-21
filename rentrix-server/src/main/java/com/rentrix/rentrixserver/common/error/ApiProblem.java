package com.rentrix.rentrixserver.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.List;

/**
 * RFC 9457 Problem Details body used for every error response (D-60).
 * {@code code} is the stable machine code clients switch on; {@code traceId} matches the
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
		HttpStatus resolved = HttpStatus.resolve(status.value());
		String title = resolved != null ? resolved.getReasonPhrase() : "Error";
		return new ApiProblem(TYPE_BASE + code.replace('_', '-'), title, status.value(), code, detail, instance,
			errors == null ? List.of() : errors, traceId);
	}
	
	/** One invalid input field. */
	public record FieldError(String field, String code, String message) {
	}
	
}
