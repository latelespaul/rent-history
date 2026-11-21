package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.auth.google.GoogleIdentity;
import com.rentrix.rentrixserver.auth.google.GoogleTokenVerifier;
import com.rentrix.rentrixserver.auth.google.InvalidGoogleTokenException;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserRoles;
import com.rentrix.rentrixserver.user.UserStatus;
import com.rentrix.rentrixserver.user.UserView;
import com.rentrix.rentrixserver.user.UserViews;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Sign-in, refresh and logout (§13). Google is the production login (D-70); dev login reuses
 * {@link #startSession} for seed users (D-77). Both return the API's own access token (D-62) and a new
 * refresh token family (D-63); the Google token is never stored.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
	
	static final int DISPLAY_NAME_MAX = 60;
	
	private final UserRepository users;
	private final GoogleTokenVerifier googleTokens;
	private final RefreshTokenService refreshTokens;
	private final AccessTokenService accessTokens;
	private final UserRoles userRoles;
	private final UserViews userViews;
	private final AuthProperties properties;
	private final Clock clock;
	
	/** A started or refreshed session: the access token, the raw refresh token (cookie) and the user. */
	public record Session(String accessToken, String refreshToken, UserView user) {
	}
	
	/**
	 * Verifies the Google ID token, then finds the user by {@code google_sub} or creates one (D-72, D-73).
	 * Email, verification time, Google name and avatar are refreshed on every sign-in; the display name only
	 * when the user is new.
	 */
	@Transactional
	public Session signInWithGoogle(String idToken) {
		if (!properties.googleEnabled()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "google_sign_in_disabled",
				"Google sign-in is not configured on this server.");
		}
		GoogleIdentity identity;
		try {
			identity = googleTokens.verify(idToken);
		} catch (InvalidGoogleTokenException e) {
			throw googleTokenInvalid();
		}
		if (!identity.emailVerified() || !StringUtils.hasText(identity.email())
			|| !StringUtils.hasText(identity.sub())) {
			throw googleTokenInvalid();
		}
		String email = identity.email().trim().toLowerCase(Locale.ROOT);
		Optional<User> bySub = users.findByGoogleSub(identity.sub());
		Optional<User> byEmail = users.findByEmail(email);
		if (byEmail.isPresent() && (bySub.isEmpty() || !byEmail.get().getId().equals(bySub.get().getId()))) {
			// Never merge accounts automatically; an admin resolves it (D-72).
			throw ApiException.conflict("account_email_conflict",
				"This Google account's email is already used by another Rentrix account. Contact support.");
		}
		Instant now = now();
		User user = bySub.orElseGet(() -> newGoogleUser(identity, email));
		requireActive(user);
		if (!email.equals(user.getEmail()) || user.getEmailVerifiedAt() == null) {
			user.setEmailVerifiedAt(now);
		}
		user.setEmail(email);
		user.setGoogleName(identity.name());
		user.setAvatarUrl(identity.pictureUrl());
		user.setLastLoginAt(now);
		return startSession(users.save(user));
	}
	
	/** Starts a session for an existing user who already passed a sign-in check. */
	@Transactional
	public Session startSession(User user) {
		requireActive(user);
		user.setLastLoginAt(now());
		String refresh = refreshTokens.issue(user, UUID.randomUUID());
		return new Session(accessToken(user), refresh, userViews.of(user));
	}
	
	/** Rotates the refresh token (D-63); roles are re-read from the database for the new access token. */
	@Transactional(noRollbackFor = ApiException.class)
	public Session refresh(String refreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
		return new Session(accessToken(rotation.user()), rotation.token(), userViews.of(rotation.user()));
	}
	
	@Transactional
	public void logout(String refreshToken) {
		refreshTokens.revoke(refreshToken);
	}
	
	public String accessToken(User user) {
		return accessTokens.issue(user.getPublicId(), userRoles.effective(user));
	}
	
	/** Suspended or deleted users cannot sign in or refresh (§13). */
	static void requireActive(User user) {
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw RefreshTokenService.suspended();
		}
	}
	
	private static User newGoogleUser(GoogleIdentity identity, String email) {
		User user = new User();
		user.setGoogleSub(identity.sub());
		user.setDisplayName(initialDisplayName(identity.name(), email));
		return user;
	}
	
	/** Google name, else the email's local part; 2–60 characters (§5.1). */
	static String initialDisplayName(String googleName, String email) {
		String name = StringUtils.hasText(googleName) ? googleName.trim() : email.substring(0, email.indexOf('@'));
		if (name.length() < 2) {
			name = "Rentrix user";
		}
		return name.length() > DISPLAY_NAME_MAX ? name.substring(0, DISPLAY_NAME_MAX).trim() : name;
	}
	
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}
	
	private static ApiException googleTokenInvalid() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "google_token_invalid",
			"Google sign-in failed. Try again.");
	}
	
}
