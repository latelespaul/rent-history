package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.admin.AdminConsoleMembers;
import com.rentrix.rentrixserver.auth.google.GoogleIdentity;
import com.rentrix.rentrixserver.auth.google.GoogleTokenVerifier;
import com.rentrix.rentrixserver.auth.google.InvalidGoogleTokenException;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.invite.InviteService;
import com.rentrix.rentrixserver.notification.email.WelcomeMailer;
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
 * refresh token family (D-63); the Google token is never stored. The admin console (§13.3) has its own sign-in,
 * session and refresh ({@link #signInAdminWithGoogle}, {@link #startAdminSession}, {@link #refreshAdmin}), open only
 * to {@link AdminConsoleMembers}; main-site sessions never carry {@code ADMIN} (D-19).
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
	private final AdminConsoleMembers adminMembers;
	private final WelcomeMailer welcome;
	private final InviteService invites;
	private final Clock clock;
	
	/**
	 * A started or refreshed session: the access token, the raw refresh token (cookie) and the user. {@code admin}
	 * marks an admin console session, which gets the admin cookie.
	 */
	public record Session(String accessToken, String refreshToken, UserView user, boolean admin) {
		
		public Session(String accessToken, String refreshToken, UserView user) {
			this(accessToken, refreshToken, user, false);
		}
		
	}
	
	/**
	 * Verifies the Google ID token, then finds the user by {@code google_sub} or creates one (D-72, D-73).
	 * Email, verification time, Google name and avatar are refreshed on every sign-in; the display name only
	 * when the user is new. A new user also gets the welcome email (D-172).
	 */
	@Transactional
	public Session signInWithGoogle(String idToken) {
		return signInWithGoogle(idToken, null);
	}
	
	/**
	 * Like {@link #signInWithGoogle(String)}; when this sign-in creates the account, {@code ref} (an invite code
	 * the web kept from a {@code ?ref=} link) counts the new user for the code's owner (invite-friends IF-5). The
	 * reply is the same whatever happens to {@code ref}.
	 */
	@Transactional
	public Session signInWithGoogle(String idToken, String ref) {
		GoogleIdentity identity = verifiedGoogle(idToken);
		boolean signUp = users.findByGoogleSub(identity.sub()).isEmpty();
		User user = googleUser(identity, email(identity));
		if (signUp) {
			welcome.signedUp(user);
			invites.attribute(user, ref);
		}
		return startSession(user);
	}
	
	/**
	 * Admin console sign-in (§13.3): like {@link #signInWithGoogle}, but only for an {@link AdminConsoleMembers}
	 * member, checked before any account is created, so a stranger on the admin host leaves no user row behind.
	 */
	@Transactional
	public Session signInAdminWithGoogle(String idToken) {
		GoogleIdentity identity = verifiedGoogle(idToken);
		String email = email(identity);
		boolean dbAdmin = users.findByGoogleSub(identity.sub()).filter(adminMembers::hasRoleRow).isPresent();
		if (!dbAdmin && !adminMembers.isListed(email)) {
			throw notAnAdmin();
		}
		return startAdminSession(googleUser(identity, email));
	}
	
	private GoogleIdentity verifiedGoogle(String idToken) {
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
		return identity;
	}
	
	private static String email(GoogleIdentity identity) {
		return identity.email().trim().toLowerCase(Locale.ROOT);
	}
	
	/** Finds the user by {@code google_sub} or creates one, and refreshes the Google fields (D-72). */
	private User googleUser(GoogleIdentity identity, String email) {
		Optional<User> bySub = users.findByGoogleSub(identity.sub());
		Optional<User> byEmail = users.findByEmail(email);
		if (byEmail.isPresent() && (bySub.isEmpty() || !byEmail.get().getId().equals(bySub.get().getId()))) {
			// Never merge accounts automatically; an admin resolves it (D-72).
			throw ApiException.conflict("account_email_conflict",
				"This Google account's email is already used by another account. Contact support.");
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
		return users.save(user);
	}
	
	/** Starts a session for an existing user who already passed a sign-in check. */
	@Transactional
	public Session startSession(User user) {
		requireActive(user);
		user.setLastLoginAt(now());
		String refresh = refreshTokens.issue(user, UUID.randomUUID());
		return new Session(accessTokens.issue(user.getPublicId(), userRoles.effective(user), now()), refresh,
			userViews.of(user));
	}
	
	/** Rotates the refresh token (D-63); roles are re-read from the database for the new access token. */
	@Transactional(noRollbackFor = ApiException.class)
	public Session refresh(String refreshToken) {
		if (RefreshTokenService.isAdmin(refreshToken)) {
			throw RefreshTokenService.invalid();
		}
		RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
		User user = rotation.user();
		return new Session(accessTokens.issue(user.getPublicId(), userRoles.effective(user), rotation.signedInAt()),
			rotation.token(), userViews.of(user));
	}
	
	/** Starts an admin console session (§13.3) for a member who passed a sign-in check. */
	@Transactional
	public Session startAdminSession(User user) {
		requireActive(user);
		if (!adminMembers.isMember(user)) {
			throw notAnAdmin();
		}
		user.setLastLoginAt(now());
		String refresh = refreshTokens.issue(user, UUID.randomUUID(), true);
		return new Session(accessTokens.issueAdmin(user.getPublicId()), refresh, userViews.of(user), true);
	}
	
	/**
	 * Rotates an admin console refresh token. Membership is checked again, so an email removed from
	 * {@code ADMIN_CONSOLE_EMAILS} ends the session at its next refresh ({@code 403 not_an_admin}).
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public Session refreshAdmin(String refreshToken) {
		if (!RefreshTokenService.isAdmin(refreshToken)) {
			throw RefreshTokenService.invalid();
		}
		RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
		User user = rotation.user();
		if (!adminMembers.isMember(user)) {
			refreshTokens.revoke(rotation.token());
			throw notAnAdmin();
		}
		return new Session(accessTokens.issueAdmin(user.getPublicId()), rotation.token(), userViews.of(user), true);
	}
	
	@Transactional
	public void logout(String refreshToken) {
		refreshTokens.revoke(refreshToken);
	}
	
	/** A fresh access token for the caller's running session (it keeps the session's {@code auth_time}). */
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
			name = "Member";
		}
		return name.length() > DISPLAY_NAME_MAX ? name.substring(0, DISPLAY_NAME_MAX).trim() : name;
	}
	
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}
	
	static ApiException notAnAdmin() {
		return new ApiException(HttpStatus.FORBIDDEN, "not_an_admin",
			"This account is not an admin of the console.");
	}
	
	private static ApiException googleTokenInvalid() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "google_token_invalid",
			"Google sign-in failed. Try again.");
	}
	
}
