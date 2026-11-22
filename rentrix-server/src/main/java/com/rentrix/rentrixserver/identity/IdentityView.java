package com.rentrix.rentrixserver.identity;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /me/identity}: the caller's own checks, masked only (D-106). Shown to the owner (and later admins)
 * only; nobody else sees which method was used or any masked value (D-57).
 *
 * @param methods one entry per method, in a fixed order: Aadhaar, PAN, phone OTP, LinkedIn
 */
public record IdentityView(boolean idVerified, boolean linkedinVerified, List<Method> methods) {
	
	/**
	 * @param status    null when the method was never started
	 * @param masked    PAN {@code XXXXXX234F}, Aadhaar {@code XXXX XXXX 1234}, phone {@code +91 XXXXXX3210}; for
	 *                  LinkedIn the caller's own normalised profile URL; null when nothing is stored
	 * @param available whether the caller can start this method now (phone OTP needs an SMS backend; PAN and
	 *                  Aadhaar need the document upload of PLAN B12)
	 */
	public record Method(IdentityMethod method, IdentityStatus status, String masked, CheckedBy checkedBy,
		Instant verifiedAt, Reject reject, boolean available) {
	}
	
	/** Why the check was rejected or revoked; the note is meant for the user. */
	public record Reject(IdentityRejectCode code, String note) {
	}
	
}
