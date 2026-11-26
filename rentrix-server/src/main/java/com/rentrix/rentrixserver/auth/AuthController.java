package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.auth.dev.DevLoginController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public auth endpoints (§12.2, §13). */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
	
	private final AuthService auth;
	private final AuthProperties properties;
	private final SignInRateLimiter rateLimiter;
	private final SessionResponses sessions;
	private final ObjectProvider<DevLoginController> devLogin;
	
	/** What the login page offers, so the web needs no build-time Google config (D-76). */
	public record AuthConfig(String googleClientId, boolean devLogin) {
	}
	
	/** {@code ref}: an invite code from a {@code ?ref=} link, counted only when this sign-in creates the account. */
	public record GoogleSignInRequest(@NotBlank @Size(max = 8192) String idToken, @Size(max = 32) String ref) {
	}
	
	@GetMapping("/config")
	AuthConfig config() {
		return new AuthConfig(properties.google().clientId(), devLogin.getIfAvailable() != null);
	}
	
	@PostMapping("/google")
	ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleSignInRequest body, HttpServletRequest request) {
		rateLimiter.check(request);
		return sessions.signedIn(request, auth.signInWithGoogle(body.idToken(), body.ref()));
	}
	
	@PostMapping("/refresh")
	ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
		String raw = RefreshCookies.read(request);
		AuthService.Session session;
		try {
			session = auth.refresh(raw);
		} catch (PessimisticLockingFailureException locked) {
			// The database refused a lock (SQLITE_BUSY) and the rotation rolled back whole: try once more, as
			// IdempotencyFilter.complete does. A failed retry still answers 409 concurrent_update, never 401.
			session = auth.refresh(raw);
		}
		return sessions.signedIn(request, session);
	}
	
	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		auth.logout(RefreshCookies.read(request));
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookies.clear(request)).build();
	}
	
}
