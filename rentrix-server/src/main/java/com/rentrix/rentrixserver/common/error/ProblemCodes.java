package com.rentrix.rentrixserver.common.error;

import org.springframework.http.HttpStatusCode;

/** Default machine codes for HTTP statuses that no handler names more precisely. */
final class ProblemCodes {
	
	private ProblemCodes() {
	}
	
	static String forStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "malformed_request";
			case 401 -> "unauthenticated";
			case 403 -> "forbidden";
			case 404 -> "not_found";
			case 405 -> "method_not_allowed";
			case 406 -> "not_acceptable";
			case 409 -> "conflict";
			case 413 -> "payload_too_large";
			case 415 -> "unsupported_media_type";
			case 422 -> "validation_failed";
			case 429 -> "rate_limited";
			default -> status.is5xxServerError() ? "internal_error" : "error";
		};
	}
	
}
