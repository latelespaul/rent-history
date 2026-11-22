package com.rentrix.rentrixserver.location;

import java.util.Locale;

/** Name normalisation shared by every location level: the display form and the unique {@code name_key}. */
public final class LocationNames {
	
	private LocationNames() {
	}
	
	/** Trimmed, single-spaced display name; null stays null. */
	public static String clean(String name) {
		return name == null ? null : name.trim().replaceAll("\\s+", " ");
	}
	
	/** Lower-cased {@link #clean} form, unique per parent (city, area, society or tower). */
	public static String key(String name) {
		String cleaned = clean(name);
		return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
	}
	
}
