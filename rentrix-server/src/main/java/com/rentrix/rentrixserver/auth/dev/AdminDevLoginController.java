package com.rentrix.rentrixserver.auth.dev;

import com.rentrix.rentrixserver.admin.AdminConsoleMembers;
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

/**
 * Dev-only admin console login (D-77, §13.3): the demo seed users who are console members (the seed's Demo Admin),
 * signed in to an admin session. Same profiles and flag as {@link DevLoginController}, and on the admin host only
 * ({@code AdminHostFilter}).
 */
@RestController
@RequestMapping("/auth/admin")
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "app.dev-login", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AdminDevLoginController {
	
	private final UserRepository users;
	private final UserViews userViews;
	private final AuthService auth;
	private final SignInRateLimiter rateLimiter;
	private final SessionResponses sessions;
	private final AdminConsoleMembers members;
	
	@GetMapping("/dev-users")
	@Transactional(readOnly = true)
	public List<UserView> devUsers() {
		return users.findByDemoTrueAndStatusOrderByDisplayNameAsc(UserStatus.ACTIVE).stream()
			.filter(members::isMember).map(userViews::of).toList();
	}
	
	@PostMapping("/dev-login")
	@Transactional
	public ResponseEntity<AuthResponse> devLogin(@Valid @RequestBody DevLoginController.DevLoginRequest body,
		HttpServletRequest request) {
		rateLimiter.check(request);
		User user = users.findByPublicId(body.userId())
			.filter(User::isDemo)
			.filter(members::isMember)
			.orElseThrow(() -> ApiException.notFound("Demo admin"));
		return sessions.signedIn(request, auth.startAdminSession(user));
	}
	
}
