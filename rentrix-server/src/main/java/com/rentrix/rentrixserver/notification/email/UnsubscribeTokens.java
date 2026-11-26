package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.security.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * The unsubscribe link's token (D-172): {@code <user public id>.<HMAC-SHA256 of it>}, keyed by a key derived from
 * {@code JWT_SECRET}, so it is unguessable and cannot be made for another user. It never expires: an old email's
 * link keeps working. A new {@code JWT_SECRET} turns old links off.
 */
@Component
public class UnsubscribeTokens {
	
	private static final String ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	
	private final SecretKeySpec key;
	
	public UnsubscribeTokens(JwtProperties jwt) {
		// Its own key, so a token here is never valid anywhere else the JWT secret is used.
		byte[] derived = mac(new SecretKeySpec(jwt.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM),
			"rentrix-mail-unsubscribe-v1");
		this.key = new SecretKeySpec(derived, ALGORITHM);
	}
	
	public String issue(UUID userPublicId) {
		return userPublicId + "." + ENCODER.encodeToString(mac(key, userPublicId.toString()));
	}
	
	/** The user the token was issued for, or empty when it is malformed or its signature does not match. */
	public Optional<UUID> verify(String token) {
		if (token == null) {
			return Optional.empty();
		}
		int dot = token.indexOf('.');
		if (dot <= 0) {
			return Optional.empty();
		}
		UUID publicId;
		try {
			publicId = UUID.fromString(token.substring(0, dot));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		byte[] expected = ENCODER.encodeToString(mac(key, publicId.toString())).getBytes(StandardCharsets.US_ASCII);
		byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
		// The id must also print back exactly as given, so one user has exactly one valid token.
		boolean idExact = publicId.toString().equals(token.substring(0, dot));
		return idExact && MessageDigest.isEqual(expected, given) ? Optional.of(publicId) : Optional.empty();
	}
	
	private static byte[] mac(SecretKeySpec key, String data) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 is not available", e);
		}
	}
	
}
