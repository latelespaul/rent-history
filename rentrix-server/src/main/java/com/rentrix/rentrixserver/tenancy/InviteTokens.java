package com.rentrix.rentrixserver.tenancy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Invite token values (§5.12): 256 random bits sent once by email, stored only as a SHA-256 hash. */
final class InviteTokens {
	
	private static final SecureRandom RANDOM = new SecureRandom();
	
	private InviteTokens() {
	}
	
	static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
	
	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}
	
}
