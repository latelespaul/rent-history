package com.rentrix.rentrixserver.verification;

/**
 * The green tick of one property, tenancy or review (D-134): every item is visible at once as
 * {@code UNVERIFIED} ("Not verified yet"); an admin's approval makes it {@code VERIFIED} ("Verified by Doorway
 * Living"); a rejection leaves it visible as {@code REJECTED}, with no tick. A null column reads as
 * {@code UNVERIFIED}. Users carry the same state per role in their verification application.
 */
public enum VerificationState {
	UNVERIFIED, VERIFIED, REJECTED;
	
	/** A stored value, null read as {@code UNVERIFIED}. */
	public static VerificationState of(VerificationState stored) {
		return stored == null ? UNVERIFIED : stored;
	}
	
}
