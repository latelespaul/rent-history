package com.rentrix.rentrixserver.metrics;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns what the browser sent into the few coarse values the traffic counter may keep (D-175). A path keeps only
 * the route's fixed words, every id becomes {@code {id}} and the query is dropped; a source is a short label
 * ({@code invite}, a {@code utm_source}, or the referrer's site), never a full referrer URL and never a ref code.
 */
final class TrafficNormaliser {
	
	/** The fixed words of the web routes; any other segment is an id (or junk) and becomes {@code {id}}. */
	private static final Set<String> WORDS = Set.of("p", "s", "u", "search", "login", "contact", "terms", "privacy",
		"welcome", "created", "verify", "settings", "delete-account", "me", "tenancies", "new", "homes", "edit", "review",
		"tenancy-invite", "dashboard", "properties");
	
	private static final int MAX_SEGMENTS = 4;
	private static final Pattern LABEL = Pattern.compile("[a-z0-9][a-z0-9._-]{0,39}");
	private static final Map<String, String> SITES = Map.ofEntries(
		Map.entry("wa.me", "whatsapp"), Map.entry("whatsapp.com", "whatsapp"), Map.entry("t.me", "telegram"),
		Map.entry("telegram.org", "telegram"), Map.entry("google.com", "google"), Map.entry("google.co.in", "google"),
		Map.entry("bing.com", "bing"), Map.entry("duckduckgo.com", "duckduckgo"), Map.entry("facebook.com", "facebook"),
		Map.entry("l.facebook.com", "facebook"), Map.entry("instagram.com", "instagram"),
		Map.entry("l.instagram.com", "instagram"), Map.entry("t.co", "twitter"), Map.entry("twitter.com", "twitter"),
		Map.entry("x.com", "twitter"), Map.entry("linkedin.com", "linkedin"), Map.entry("lnkd.in", "linkedin"));
	
	private TrafficNormaliser() {
	}
	
	/** {@code /p/8f3a…?x=1} → {@code /p/{id}}; anything outside the known routes → {@code /other}. */
	static String path(String raw) {
		if (raw == null) {
			return "/other";
		}
		String p = raw.trim();
		int cut = indexOfAny(p, '?', '#');
		if (cut >= 0) {
			p = p.substring(0, cut);
		}
		String[] parts = p.split("/");
		StringBuilder out = new StringBuilder();
		int count = 0;
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (++count > MAX_SEGMENTS) {
				return "/other";
			}
			String word = part.toLowerCase(Locale.ROOT);
			if (count == 1 && !WORDS.contains(word)) {
				return "/other";
			}
			out.append('/').append(WORDS.contains(word) ? word : "{id}");
		}
		return out.isEmpty() ? "/" : out.toString();
	}
	
	/**
	 * The client names a source only for an invite link ({@code invite}), a {@code utm_source} or in-site
	 * navigation ({@code internal}); otherwise it comes from the referrer's host, or {@code direct}.
	 */
	static String source(String source, String referrer, String ownHost) {
		String label = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
		if (LABEL.matcher(label).matches()) {
			return label;
		}
		String host = host(referrer);
		if (host == null) {
			return "direct";
		}
		if (host.equalsIgnoreCase(ownHost)) {
			return "internal";
		}
		String known = SITES.get(host);
		if (known != null) {
			return known;
		}
		if (host.startsWith("google.")) {
			return "google";
		}
		return host.length() <= 40 ? host : host.substring(0, 40);
	}
	
	private static String host(String referrer) {
		if (referrer == null || referrer.isBlank()) {
			return null;
		}
		try {
			String host = URI.create(referrer.trim()).getHost();
			if (host == null) {
				return null;
			}
			host = host.toLowerCase(Locale.ROOT);
			return host.startsWith("www.") ? host.substring(4) : host;
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}
	
	private static int indexOfAny(String s, char a, char b) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == a || s.charAt(i) == b) {
				return i;
			}
		}
		return -1;
	}
	
}
