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
 * rotated on every use. Rotation is a conditional update, so two simultaneous rotations of one token
 * cannot both succeed. Presenting the family's most recently rotated token again within the reuse grace
 * window (default 30 s, e.g. two tabs refreshing at once) returns an access token for the current successor
 * without rotating again. Any other reuse of a rotated or revoked token revokes its whole family (theft
 * detection). An admin console family (§13.3) has raw values starting with {@link #ADMIN_PREFIX}; its successors keep
 * the prefix, and the hash covers it, so a main-site token cannot be turned into an admin one.
 */
@Slf4j
@Service
public class RefreshTokenService {
	
	private static final SecureRandom RANDOM = new SecureRandom();
	static final String ADMIN_PREFIX = "adm.";
	
	private final RefreshTokenRepository tokens;
	private final Duration ttl;
	private final Duration reuseGrace;
	private final Clock clock;
	
	public RefreshTokenService(RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
		this.tokens = tokens;
		this.ttl = properties.refreshTokenTtl();
		this.reuseGrace = properties.refreshReuseGrace();
		this.clock = clock;
	}
	
	public Duration ttl() {
		return ttl;
	}
	
	/** Stores a new main-site token in {@code familyId} and returns its raw value (the cookie value). */
	@Transactional
	public String issue(User user, UUID familyId) {
		return issue(user, familyId, false);
	}
	
	/** Stores a new token in {@code familyId}, an admin console one when {@code admin}, and returns its raw value. */
	@Transactional
	public String issue(User user, UUID familyId, boolean admin) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String raw = (admin ? ADMIN_PREFIX : "") + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		RefreshToken token = new RefreshToken();
		token.setUser(user);
		token.setTokenHash(hash(raw));
		token.setFamilyId(familyId);
		token.setExpiresAt(now().plus(ttl));
		tokens.save(token);
		return raw;
	}
	
	/**
	 * A rotated token: the owner and the raw value of its replacement in the same family, or {@code null}
	 * when a grace-window reuse kept the current successor (the caller keeps its cookie).
	 */
	public record Rotation(User user, String token) {
	}
	
	/**
	 * Revokes {@code raw} and issues its successor. Revocations made before an error is thrown are committed
	 * ({@code noRollbackFor}), so a reused token's family stays revoked. The revoking UPDATE runs first, before
	 * any read, so a concurrent write elsewhere makes this wait (busy timeout) instead of fail (D-11).
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public Rotation rotate(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw invalid();
		}
		Instant now = now();
		boolean rotated = tokens.revokeActiveByHash(hash(raw), now) == 1;
		RefreshToken token = find(raw);
		User user = token.getUser();
		if (rotated) {
			requireActive(token, user, now);
			return new Rotation(user, issue(user, token.getFamilyId(), isAdmin(raw)));
		}
		if (token.getRevokedAt() == null) {
			throw invalid(); // expired
		}
		if (isLatestRotationWithinGrace(token, now)) {
			requireActive(token, user, now);
			return new Rotation(user, null);
		}
		log.warn("Refresh token reuse detected; revoking token family {}", token.getFamilyId());
		revokeFamily(token.getFamilyId(), now);
		throw invalid();
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
	
	/** True for a raw value of an admin console family. */
	public static boolean isAdmin(String raw) {
		return raw != null && raw.startsWith(ADMIN_PREFIX);
	}
	
	private RefreshToken find(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw invalid();
		}
		return tokens.findByTokenHash(hash(raw)).orElseThrow(RefreshTokenService::invalid);
	}
	
	/** The token was rotated within the grace window, no later rotation followed, and its successor is live. */
	private boolean isLatestRotationWithinGrace(RefreshToken token, Instant now) {
		Instant revokedAt = tokens.findRevokedAtById(token.getId());
		return revokedAt != null && revokedAt.plus(reuseGrace).isAfter(now)
			&& !tokens.existsByFamilyIdAndRevokedAtAfter(token.getFamilyId(), revokedAt)
			&& tokens.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(token.getFamilyId(), now);
	}
	
	private void requireActive(RefreshToken token, User user, Instant now) {
		if (user.getStatus() != UserStatus.ACTIVE) {
			revokeFamily(token.getFamilyId(), now);
			throw suspended();
		}
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
