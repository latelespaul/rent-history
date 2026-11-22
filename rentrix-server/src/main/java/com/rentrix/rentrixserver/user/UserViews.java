package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.verification.VerificationStatuses;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds {@link UserView}; call inside a transaction (the home city is a lazy reference). */
@Component
@RequiredArgsConstructor
public class UserViews {
	
	private final UserRoles userRoles;
	private final VerificationStatuses verification;
	
	public UserView of(User user) {
		return new UserView(
			user.getPublicId(),
			user.getDisplayName(),
			user.getEmail(),
			user.getEmailVerifiedAt() != null,
			user.getAvatarUrl(),
			user.getPhone(),
			userRoles.effective(user).stream().sorted().toList(),
			user.getHomeCity() == null ? null : user.getHomeCity().getCode(),
			user.getStatus(),
			user.isDemo(),
			user.getOnboardedAt() == null,
			user.getOnboardedAt(),
			verification.of(user),
			user.getCreatedAt());
	}
	
}
