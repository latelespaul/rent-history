package com.rentrix.rentrixserver.identity;

/**
 * A way to verify a user (§5.15). {@code AADHAAR}, {@code PAN} and {@code PHONE_OTP} are ID methods: any one
 * of them {@code VERIFIED} makes the user "ID verified" (D-99). {@code LINKEDIN} is a bonus badge only and
 * never counts as ID (D-100).
 */
public enum IdentityMethod {
	AADHAAR, PAN, PHONE_OTP, LINKEDIN;
	
	public boolean isIdMethod() {
		return this != LINKEDIN;
	}
	
}
