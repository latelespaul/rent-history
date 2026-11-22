package com.rentrix.rentrixserver.identity;

/**
 * A way to verify a user (§5.15). {@code PHONE_OTP} is required to post (D-117, D-132). {@code AADHAAR} and
 * {@code PAN} are the optional extra ID: either one {@code VERIFIED} gives the "ID verified" badge (D-100).
 * {@code LINKEDIN} is a bonus badge only and never counts as ID.
 */
public enum IdentityMethod {
	AADHAAR, PAN, PHONE_OTP, LINKEDIN;
	
	/** Counts toward the "ID verified" badge (D-100): PAN or masked Aadhaar. */
	public boolean isIdMethod() {
		return this == AADHAAR || this == PAN;
	}
	
}
