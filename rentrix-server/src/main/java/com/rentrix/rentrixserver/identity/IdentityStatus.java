package com.rentrix.rentrixserver.identity;

/** State of one identity check (§5.15). */
public enum IdentityStatus {
	/** Submitted, waiting for an admin check (PAN, Aadhaar, LinkedIn). */
	PENDING,
	VERIFIED,
	REJECTED,
	/** Was verified, no longer counts, e.g. {@code PHONE_CHANGED}. */
	REVOKED
}
