package com.rentrix.rentrixserver.identity;

/**
 * The two public yes/no badges (D-100). {@code idVerified}: PAN or masked Aadhaar is verified (design v4: a verified
 * phone alone gives no badge, D-117). {@code linkedinVerified}: the optional LinkedIn check is verified; a bonus.
 * Neither says which method was used.
 */
public record Badges(boolean idVerified, boolean linkedinVerified) {
	
	public static final Badges NONE = new Badges(false, false);
	
}
