package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.user.UserView;

/**
 * Body of every sign-in and refresh response (§12.2). The refresh token travels only in the cookie.
 *
 * @param needsOnboarding true until {@code POST /me/onboarding} is done (D-73)
 */
public record AuthResponse(String accessToken, UserView user, boolean needsOnboarding) {
	
	static AuthResponse of(AuthService.Session session) {
		return new AuthResponse(session.accessToken(), session.user(), session.user().needsOnboarding());
	}
	
}
