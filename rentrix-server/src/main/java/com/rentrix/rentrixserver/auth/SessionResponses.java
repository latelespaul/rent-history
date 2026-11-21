package com.rentrix.rentrixserver.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Turns a started session into {@code 200} with the {@link AuthResponse} body and the refresh cookie. A
 * grace-window refresh (D-63) has no new refresh token, so the browser keeps the cookie it already has.
 */
@Component
@RequiredArgsConstructor
public class SessionResponses {
	
	private final RefreshTokenService refreshTokens;
	
	public ResponseEntity<AuthResponse> signedIn(HttpServletRequest request, AuthService.Session session) {
		ResponseEntity.BodyBuilder response = ResponseEntity.ok();
		if (session.refreshToken() != null) {
			response.header(HttpHeaders.SET_COOKIE, RefreshCookies.issue(request, session.refreshToken(), refreshTokens.ttl()));
		}
		return response.body(AuthResponse.of(session));
	}
	
}
