package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only trust write behind {@link ApprovalGuard}, so the gate's HTTP shape is tested before B5–B7 use it. */
@RestController
@RequiredArgsConstructor
class GatedWriteTestController {
	
	private final ApprovalGuard guard;
	private final UserRepository users;
	
	@PostMapping("/test-only/gated/{role}")
	Map<String, String> write(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Role role) {
		User user = users.findByPublicId(caller.publicId()).orElseThrow(() -> ApiException.notFound("User"));
		guard.require(user, role);
		return Map.of("written", role.name());
	}
	
}
