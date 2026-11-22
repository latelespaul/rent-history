package com.rentrix.rentrixserver.verification;

/** What happened to an application (§5.13). */
public enum VerificationEventType {
	SUBMITTED, APPROVED, REJECTED, REVOKED,
	/** An admin decided one of the user's PAN or Aadhaar checks while deciding the application (D-105). */
	IDENTITY_CHECKED,
	/** The user verified their phone; {@code code} is the method ({@code TRUECALLER} or {@code SMS_OTP}). */
	PHONE_VERIFIED
}
