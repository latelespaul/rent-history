package com.rentrix.rentrixserver.identity;

/**
 * The two public yes/no badges (D-100). {@code idVerified}: at least one of Aadhaar, PAN or phone OTP is
 * verified (D-99). {@code linkedinVerified}: the optional LinkedIn check is verified; a bonus, never required.
 * Neither says which method was used.
 */
public record Badges(boolean idVerified, boolean linkedinVerified) {
	
	public static final Badges NONE = new Badges(false, false);
	
}
