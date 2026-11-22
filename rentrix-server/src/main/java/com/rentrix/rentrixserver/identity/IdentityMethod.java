package com.rentrix.rentrixserver.identity;

/**
 * A way to verify a user (§5.15). A verified phone is required to post (D-117, D-132): {@code PHONE_OTP} (SMS
 * code) or {@code TRUECALLER} (the number Truecaller verified), whichever came first. {@code AADHAAR} and
 * {@code PAN} are the optional extra ID: either one {@code VERIFIED} gives the "ID verified" badge (D-100).
 * {@code LINKEDIN} is a bonus badge only and never counts as ID.
 */
public enum IdentityMethod {
	AADHAAR, PAN, PHONE_OTP, LINKEDIN, TRUECALLER;
	
	/** Counts toward the "ID verified" badge (D-100): PAN or masked Aadhaar. */
	public boolean isIdMethod() {
		return this == AADHAAR || this == PAN;
	}
	
	/** Verifies the account's phone: SMS code or Truecaller. */
	public boolean isPhoneMethod() {
		return this == PHONE_OTP || this == TRUECALLER;
	}
	
}
