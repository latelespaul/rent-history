package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens (D-63): opaque 256-bit random values, stored only as a SHA-256 hash, 30-day lifetime,
 * rotated on every use. Presenting a token that was already rotated or revoked revokes its whole family
 * (theft detection).
 */
@Slf4j
@Service
public class RefreshTokenService {
	
	private static final SecureRandom RANDOM = new SecureRandom();
	
	private final RefreshTokenRepository tokens;
	private final Duration ttl;
	private final Clock clock;
	
	public RefreshTokenService(RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
		this.tokens = tokens;
		this.ttl = properties.refreshTokenTtl();
		this.clock = clock;
	}
	
	public Duration ttl() {
		return ttl;
	}
	
	/** Stores a new token in {@code familyId} and returns its raw value (the cookie value). */
	@Transactional
	public String issue(User user, UUID familyId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		RefreshToken token = new RefreshToken();
		token.setUser(user);
		token.setTokenHash(hash(raw));
		token.setFamilyId(familyId);
		token.setExpiresAt(now().plus(ttl));
		tokens.save(token);
		return raw;
	}
	
	/** A rotated token: the owner and the raw value of its replacement in the same family. */
	public record Rotation(User user, String token) {
	}
	
	/**
	 * Revokes {@code raw} and issues its successor. Revocations made before an error is thrown are committed
	 * ({@code noRollbackFor}), so a reused token's family stays revoked.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public Rotation rotate(String raw) {
		RefreshToken token = find(raw);
		Instant now = now();
		if (token.getRevokedAt() != null) {
			log.warn("Refresh token reuse detected; revoking token family {}", token.getFamilyId());
			revokeFamily(token.getFamilyId(), now);
			throw invalid();
		}
		if (!token.getExpiresAt().isAfter(now)) {
			throw invalid();
		}
		User user = token.getUser();
		if (user.getStatus() != UserStatus.ACTIVE) {
			revokeFamily(token.getFamilyId(), now);
			throw suspended();
		}
		token.setRevokedAt(now);
		return new Rotation(user, issue(user, token.getFamilyId()));
	}
	
	/** Logout: revokes the token if it is known and still active; unknown values are ignored. */
	@Transactional
	public void revoke(String raw) {
		if (!StringUtils.hasText(raw)) {
			return;
		}
		tokens.findByTokenHash(hash(raw))
			.filter(t -> t.getRevokedAt() == null)
			.ifPresent(t -> t.setRevokedAt(now()));
	}
	
	private RefreshToken find(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw invalid();
		}
		return tokens.findByTokenHash(hash(raw)).orElseThrow(RefreshTokenService::invalid);
	}
	
	private void revokeFamily(UUID familyId, Instant now) {
		for (RefreshToken t : tokens.findByFamilyId(familyId)) {
			if (t.getRevokedAt() == null) {
				t.setRevokedAt(now);
			}
		}
	}
	
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}
	
	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
	
	static ApiException invalid() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "refresh_token_invalid",
			"The session has expired. Sign in again.");
	}
	
	static ApiException suspended() {
		return new ApiException(HttpStatus.FORBIDDEN, "account_suspended", "This account is suspended.");
	}
	
}
