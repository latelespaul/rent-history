package com.rentrix.rentrixserver.agreement;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks Aadhaar-like numbers in typed text before it is stored or logged (D-131): any 12 digits, optionally in
 * groups of four split by a space or a dash, that are not part of a longer number, become {@code XXXX XXXX 1234}
 * (the last four kept, the way UIDAI's masked Aadhaar shows them).
 */
public final class AadhaarMask {
	
	static final Pattern AADHAAR_LIKE = Pattern.compile("(?<![0-9])[0-9]{4}[ -]?[0-9]{4}[ -]?([0-9]{4})(?![0-9])");
	
	private AadhaarMask() {
	}
	
	/** {@code text} with every Aadhaar-like number masked; null stays null. */
	public static String mask(String text) {
		if (text == null) {
			return null;
		}
		Matcher m = AADHAAR_LIKE.matcher(text);
		return m.replaceAll(match -> "XXXX XXXX " + match.group(1));
	}
	
	/** True when {@code text} holds an Aadhaar-like number that {@link #mask} would change. */
	public static boolean contains(String text) {
		return text != null && AADHAAR_LIKE.matcher(text).find();
	}
	
}
