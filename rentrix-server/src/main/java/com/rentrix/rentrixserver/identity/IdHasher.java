package com.rentrix.rentrixserver.identity;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 with {@code ID_HASH_KEY} (D-101, D-102, D-106): the only form in which a phone number (for
 * duplicate detection), a PAN or an OTP code is compared. Without a key (a {@code prod} deployment that has not
 * set {@code ID_HASH_KEY}) nothing can be hashed, so the phone method is unavailable.
 */
@Component
public class IdHasher {
	
	private static final String ALGORITHM = "HmacSHA256";
	
	private final SecretKeySpec key;
	
	public IdHasher(IdentityProperties properties) {
		this.key = properties.hashKey() == null ? null
			: new SecretKeySpec(properties.hashKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}
	
	public boolean available() {
		return key != null;
	}
	
	/** Lower-case hex HMAC of {@code value}. */
	public String hmac(String value) {
		if (key == null) {
			throw new IllegalStateException("ID_HASH_KEY is not set");
		}
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA256 is not available", e);
		}
	}
	
	/** Constant-time comparison of two hex HMACs. */
	public static boolean same(String a, String b) {
		return a != null && b != null
			&& MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
	}
	
}
