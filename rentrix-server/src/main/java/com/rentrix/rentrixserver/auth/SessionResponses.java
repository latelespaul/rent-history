package com.rentrix.rentrixserver.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Turns a started session into {@code 200} with the {@link AuthResponse} body and the refresh cookie. */
@Component
@RequiredArgsConstructor
public class SessionResponses {
	
	private final RefreshTokenService refreshTokens;
	
	public ResponseEntity<AuthResponse> signedIn(HttpServletRequest request, AuthService.Session session) {
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, RefreshCookies.issue(request, session.refreshToken(), refreshTokens.ttl()))
			.body(AuthResponse.of(session));
	}
	
}
