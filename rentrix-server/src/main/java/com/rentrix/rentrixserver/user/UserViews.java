package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.notification.sms.PhoneMasks;
import com.rentrix.rentrixserver.verification.VerificationStatuses;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds {@link UserView}; call inside a transaction (the home city is a lazy reference). */
@Component
@RequiredArgsConstructor
public class UserViews {
	
	private final UserRoles userRoles;
	private final VerificationStatuses verification;
	private final IdentityService identity;
	
	public UserView of(User user) {
		return new UserView(
			user.getPublicId(),
			user.getDisplayName(),
			PublicNames.of(user),
			user.getEmail(),
			user.getEmailVerifiedAt() != null,
			user.getAvatarUrl(),
			PhoneMasks.mask(user.getPhone()),
			userRoles.effective(user).stream().sorted().toList(),
			user.getHomeCity() == null ? null : user.getHomeCity().getCode(),
			user.getPreferredMode(),
			user.getStatus(),
			user.isDemo(),
			user.getOnboardedAt() == null,
			user.getOnboardedAt(),
			verification.of(user),
			identity.badges(user),
			user.getCreatedAt());
	}
	
}
