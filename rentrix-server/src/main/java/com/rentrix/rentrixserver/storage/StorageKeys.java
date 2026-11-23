package com.rentrix.rentrixserver.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/** Random object keys ({@code <prefix>/<uuid>}), so a key never carries a file name and cannot leave its folder. */
public final class StorageKeys {
	
	private static final Pattern PREFIX = Pattern.compile("[a-z][a-z0-9-]{0,31}");
	private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]{0,31}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	
	private StorageKeys() {
	}
	
	public static String random(String prefix) {
		if (!PREFIX.matcher(prefix).matches()) {
			throw new IllegalArgumentException("Bad storage key prefix");
		}
		return prefix + "/" + UUID.randomUUID();
	}
	
	/** True for a key {@link #random} could have made; anything else (dots, slashes, upper case) is refused. */
	public static boolean valid(String key) {
		return key != null && KEY.matcher(key).matches();
	}
	
	static String require(String key) {
		if (!valid(key)) {
			throw new IllegalArgumentException("Bad storage key");
		}
		return key;
	}
	
}
