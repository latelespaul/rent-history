package com.rentrix.rentrixserver.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;

import java.util.List;
import java.util.Map;

/**
 * RFC 9457 Problem Details body used for every error response (D-60).
 * {@code code} is the stable machine code clients switch on and {@code title} is derived from it; {@code traceId} matches the
 * {@code X-Request-Id} response header and the log lines of the request. {@code approval} ({@code {role, status}})
 * is no longer set since open by default replaced the approval gate (D-132). {@code missing} lists the steps still to
 * do on {@code 422 verification_incomplete} (D-105) and {@code ["phone"]} on {@code 403 id_verification_required}
 * (D-117). {@code conflict} is set only on {@code 409 tenancy_overlap}: the period of the tenancy already on the home
 * ({@code from}, {@code to} months; no {@code to} while it is current) and, for that home's landlord only, its
 * {@code tenancyId}.
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
	Map<String, String> approval,
	List<String> missing,
	Map<String, String> conflict
) {

	public static final String TYPE_BASE = "https://doorwayliving.in/problems/";

	public static ApiProblem of(HttpStatusCode status, String code, String detail, String instance,
		List<FieldError> errors, String traceId) {
		return of(status, code, detail, instance, errors, traceId, null, null, null);
	}

	public static ApiProblem of(HttpStatusCode status, String code, String detail, String instance,
		List<FieldError> errors, String traceId, Map<String, String> approval, List<String> missing,
		Map<String, String> conflict) {
		return new ApiProblem(TYPE_BASE + code.replace('_', '-'), title(code), status.value(), code, detail, instance,
			errors == null ? List.of() : errors, traceId, approval == null || approval.isEmpty() ? null : approval,
			missing == null || missing.isEmpty() ? null : missing,
			conflict == null || conflict.isEmpty() ? null : conflict);
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
