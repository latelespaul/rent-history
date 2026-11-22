package com.rentrix.rentrixserver.user;

import java.util.Locale;

/** The public form of a display name (§5.1): first word and last initial, e.g. "Asha Rao" → "Asha R.". */
public final class PublicNames {
	
	private PublicNames() {
	}
	
	public static String shortName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return "Rentrix user";
		}
		String[] words = displayName.trim().split("\\s+");
		if (words.length == 1) {
			return words[0];
		}
		String last = words[words.length - 1];
		return words[0] + " " + last.substring(0, 1).toUpperCase(Locale.ROOT) + ".";
	}
	
}
