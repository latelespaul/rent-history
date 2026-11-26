package com.rentrix.rentrixserver.auth.dev;

import com.rentrix.rentrixserver.auth.AuthResponse;
import com.rentrix.rentrixserver.auth.AuthService;
import com.rentrix.rentrixserver.auth.SessionResponses;
import com.rentrix.rentrixserver.auth.SignInRateLimiter;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.notification.email.WelcomeMailer;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import com.rentrix.rentrixserver.user.UserView;
import com.rentrix.rentrixserver.user.UserViews;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev-only login (D-77): lists the seed users ({@code is_demo}) and signs in as one of them, so the demo data
 * can be clicked through and screenshotted with no Google account. The bean exists only when the {@code dev}
 * or {@code test} profile is active <em>and</em> {@code DEV_LOGIN_ENABLED=true}; elsewhere the routes are
 * {@code 404}, and {@code StartupGuard} refuses to start any other profile with the flag on.
 * {@code POST /auth/dev-login/new} signs in as a brand-new user with no role yet, so the first sign-in (role pick,
 * account created, verification stepper) can be clicked through without Google too; {@code POST /auth/dev-login}
 * also accepts such a user, so it can sign out and back in. Seed admins are not offered here:
 * they sign in on the admin host ({@link AdminDevLoginController}, D-19).
 */
@RestController
@RequestMapping("/auth")
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "app.dev-login", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevLoginController {
	
	/** {@code google_sub} prefix of the users {@code POST /dev-login/new} creates. */
	static final String NEW_USER_SUB = "dev-new-";
	
	private final UserRepository users;
	private final UserViews userViews;
	private final AuthService auth;
	private final SignInRateLimiter rateLimiter;
	private final SessionResponses sessions;
	private final WelcomeMailer welcome;
	
	public record DevLoginRequest(@NotNull UUID userId) {
	}
	
	@GetMapping("/dev-users")
	@Transactional(readOnly = true)
	public List<UserView> devUsers() {
		return users.findByDemoTrueAndStatusOrderByDisplayNameAsc(UserStatus.ACTIVE).stream()
			.filter(u -> !u.hasRole(Role.ADMIN)).map(userViews::of).toList();
	}
	
	@PostMapping("/dev-login")
	@Transactional
	public ResponseEntity<AuthResponse> devLogin(@Valid @RequestBody DevLoginRequest body, HttpServletRequest request) {
		rateLimiter.check(request);
		// A demo seed user, or one that dev-login/new made, so a fresh account can sign out and back in (e2e).
		User user = users.findByPublicId(body.userId())
			.filter(u -> u.isDemo() || u.getGoogleSub() != null && u.getGoogleSub().startsWith(NEW_USER_SUB))
			.filter(u -> !u.hasRole(Role.ADMIN))
			.orElseThrow(() -> ApiException.notFound("Demo user"));
		return sessions.signedIn(request, auth.startSession(user));
	}
	
	/**
	 * Optional {@code key} stands for one Google account: {@code dev-new-<key>} is its subject and
	 * {@code new-user-<key>@example.test} its email. Without it a random key is used.
	 */
	public record NewUserRequest(@Pattern(regexp = "[a-z0-9]{1,16}") String key) {
	}
	
	/**
	 * A new, not-onboarded, non-demo user: like a first Google sign-in, with a made-up example.test email. With a
	 * {@code key} it behaves like Google for that one account: the live user with its subject signs in again, and once
	 * that account is deleted (D-58) the same key, so the same email, makes a new, empty user.
	 */
	@PostMapping("/dev-login/new")
	@Transactional
	public ResponseEntity<AuthResponse> devLoginAsNewUser(@Valid @RequestBody(required = false) NewUserRequest body,
		HttpServletRequest request) {
		rateLimiter.check(request);
		String tag = body != null && body.key() != null ? body.key() : UUID.randomUUID().toString().substring(0, 8);
		String email = "new-user-" + tag + "@example.test";
		Optional<User> existing = users.findByGoogleSub(NEW_USER_SUB + tag);
		if (existing.isPresent()) {
			return sessions.signedIn(request, auth.startSession(existing.get()));
		}
		if (users.findByEmail(email).isPresent()) {
			throw ApiException.conflict("account_email_conflict", "This email is already used by another account.");
		}
		User user = new User();
		user.setGoogleSub(NEW_USER_SUB + tag);
		user.setEmail(email);
		user.setEmailVerifiedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
		user.setDisplayName("New User");
		User saved = users.save(user);
		welcome.signedUp(saved);
		return sessions.signedIn(request, auth.startSession(saved));
	}
	
}
