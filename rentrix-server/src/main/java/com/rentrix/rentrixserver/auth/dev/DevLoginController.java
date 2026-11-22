package com.rentrix.rentrixserver.auth.dev;

import com.rentrix.rentrixserver.auth.AuthResponse;
import com.rentrix.rentrixserver.auth.AuthService;
import com.rentrix.rentrixserver.auth.SessionResponses;
import com.rentrix.rentrixserver.auth.SignInRateLimiter;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import com.rentrix.rentrixserver.user.UserView;
import com.rentrix.rentrixserver.user.UserViews;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

import java.util.List;
import java.util.UUID;

/**
 * Dev-only login (D-77): lists the seed users ({@code is_demo}) and signs in as one of them, so the demo data
 * can be clicked through and screenshotted with no Google account. The bean exists only when the {@code dev}
 * or {@code test} profile is active <em>and</em> {@code DEV_LOGIN_ENABLED=true}; elsewhere the routes are
 * {@code 404}, and {@code StartupGuard} refuses to start any other profile with the flag on.
 */
@RestController
@RequestMapping("/auth")
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "app.dev-login", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevLoginController {
	
	private final UserRepository users;
	private final UserViews userViews;
	private final AuthService auth;
	private final SignInRateLimiter rateLimiter;
	private final SessionResponses sessions;
	
	public record DevLoginRequest(@NotNull UUID userId) {
	}
	
	@GetMapping("/dev-users")
	@Transactional(readOnly = true)
	public List<UserView> devUsers() {
		return users.findByDemoTrueAndStatusOrderByDisplayNameAsc(UserStatus.ACTIVE).stream().map(userViews::of).toList();
	}
	
	@PostMapping("/dev-login")
	@Transactional
	public ResponseEntity<AuthResponse> devLogin(@Valid @RequestBody DevLoginRequest body, HttpServletRequest request) {
		rateLimiter.check(request);
		User user = users.findByPublicId(body.userId())
			.filter(User::isDemo)
			.orElseThrow(() -> ApiException.notFound("Demo user"));
		return sessions.signedIn(request, auth.startSession(user));
	}
	
}
