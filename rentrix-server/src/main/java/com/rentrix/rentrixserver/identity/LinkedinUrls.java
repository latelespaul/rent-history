package com.rentrix.rentrixserver.identity;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and normalises a LinkedIn profile URL to {@code https://www.linkedin.com/in/<slug>} (D-93): any
 * LinkedIn host ({@code linkedin.com}, {@code www.}, a country prefix such as {@code in.}), an optional scheme,
 * a trailing slash, query string or fragment are accepted and dropped. The URL is never fetched.
 */
public final class LinkedinUrls {
	
	static final int MAX_LENGTH = 200;
	
	private static final String PREFIX = "https://www.linkedin.com/in/";
	
	private static final Pattern PROFILE = Pattern.compile(
		"^(?:https?://)?(?:[a-z]{2,3}\\.|www\\.)?linkedin\\.com/in/([\\p{L}\\p{N}_%-]{3,100})/?(?:[?#].*)?$",
		Pattern.CASE_INSENSITIVE);
	
	private LinkedinUrls() {
	}
	
	/** The normalised profile URL, or empty when the input is not a LinkedIn profile URL. */
	public static Optional<String> normalise(String input) {
		if (input == null || input.length() > 2 * MAX_LENGTH) {
			return Optional.empty();
		}
		Matcher m = PROFILE.matcher(input.trim());
		if (!m.matches()) {
			return Optional.empty();
		}
		String url = PREFIX + m.group(1).toLowerCase(Locale.ROOT);
		return url.length() <= MAX_LENGTH ? Optional.of(url) : Optional.empty();
	}
	
}
