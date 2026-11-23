package com.rentrix.rentrixserver.user;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The public form of a display name (§5.1, D-55): the first word and the initial of the last name word, e.g.
 * "Asha Rao" → "Asha R.". Words that look like codes rather than names (any digit, e.g. "BLR-1" in
 * "Demo Tenant BLR-1") are skipped, so that name is "Demo T.", not "Demo B.". With no name word after the first,
 * only the first word shows. This is the one rule: the API sends the short name, and the web shows it as-is.
 */
public final class PublicNames {
	
	/** A name word: letters, with apostrophes, hyphens or dots inside ("O'Brien", "Rao-Singh", "St."). */
	private static final Pattern NAME_WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’.\\-]*");
	
	private PublicNames() {
	}
	
	/** How a deleted account is named everywhere it still appears (D-58). */
	public static final String DELETED_USER = "Deleted user";
	
	/** The public name of {@code user}: {@link #DELETED_USER} once the account is deleted, else the short name. */
	public static String of(User user) {
		return user.getStatus() == UserStatus.DELETED || user.getDeletedAt() != null ? DELETED_USER
			: shortName(user.getDisplayName());
	}
	
	public static String shortName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return "Member";
		}
		String[] words = displayName.trim().split("\\s+");
		for (int i = words.length - 1; i >= 1; i--) {
			if (NAME_WORD.matcher(words[i]).matches()) {
				String initial = new String(Character.toChars(words[i].codePointAt(0)));
				return words[0] + " " + initial.toUpperCase(Locale.ROOT) + ".";
			}
		}
		return words[0];
	}
	
}
