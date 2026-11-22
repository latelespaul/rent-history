package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Interim statuses until verification applications exist (PLAN B11). Nothing can be submitted yet, so every held
 * role of a real user is {@code DRAFT} and the approval gate (D-91) stays closed for them. Dev seed users
 * ({@code is_demo}, D-68) count as {@code APPROVED}, as the seed plan approves every seeded party (D-97), so the
 * local demo can run the trust flows. Demo rows exist only in dev (the seeder refuses other profiles). B11 replaces
 * this bean with one that reads {@code verification_applications}.
 */
@Component
class InterimVerificationStatuses implements VerificationStatuses {
	
	@Override
	public Map<Role, VerificationStatus> of(User user) {
		VerificationStatus status = user.isDemo() ? VerificationStatus.APPROVED : VerificationStatus.DRAFT;
		Map<Role, VerificationStatus> statuses = new EnumMap<>(Role.class);
		for (Role role : new Role[] {Role.TENANT, Role.LANDLORD}) {
			if (user.hasRole(role)) {
				statuses.put(role, status);
			}
		}
		return statuses;
	}
	
}
