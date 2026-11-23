package com.rentrix.rentrixserver.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin console's sign-in, refresh and logout (§13.3), on the admin host only ({@code 404} elsewhere, see
 * {@code AdminHostFilter}). Same shapes as the main site's {@code /auth/*}, with the admin refresh cookie and an admin
 * access token; only {@code AdminConsoleMembers} get one ({@code 403 not_an_admin}).
 */
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {
	
	private final AuthService auth;
	private final SignInRateLimiter rateLimiter;
	private final SessionResponses sessions;
	
	@PostMapping("/google")
	ResponseEntity<AuthResponse> google(@Valid @RequestBody AuthController.GoogleSignInRequest body,
		HttpServletRequest request) {
		rateLimiter.check(request);
		return sessions.signedIn(request, auth.signInAdminWithGoogle(body.idToken()));
	}
	
	@PostMapping("/refresh")
	ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
		return sessions.signedIn(request, auth.refreshAdmin(RefreshCookies.read(request, true)));
	}
	
	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		auth.logout(RefreshCookies.read(request, true));
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookies.clear(request, true)).build();
	}
	
}
