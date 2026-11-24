package com.rentrix.rentrixserver.identity;

/**
 * A way to verify a user (§5.15). None is needed to post (D-163); a verified one leads to the green tick. The phone
 * is verified by {@code PHONE_OTP} (SMS code), {@code TRUECALLER} (the number Truecaller verified) or {@code TELEGRAM} (a code sent to the number's
 * Telegram through the Telegram Gateway), whichever came first. {@code AADHAAR} and
 * {@code PAN} are the optional extra ID: either one {@code VERIFIED} gives the "ID verified" badge (D-100).
 * {@code LINKEDIN} is a bonus badge only and never counts as ID.
 */
public enum IdentityMethod {
	AADHAAR, PAN, PHONE_OTP, LINKEDIN, TRUECALLER, TELEGRAM;
	
	/** Counts toward the "ID verified" badge (D-100): PAN or masked Aadhaar. */
	public boolean isIdMethod() {
		return this == AADHAAR || this == PAN;
	}
	
	/** Verifies the account's phone: SMS code, Truecaller or a code sent to Telegram. */
	public boolean isPhoneMethod() {
		return this == PHONE_OTP || this == TRUECALLER || this == TELEGRAM;
	}
	
}
