package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Until verification applications exist (PLAN B11), every held role is {@code DRAFT}: nothing has been
 * submitted, so nothing is approved and the approval gate (D-91) stays closed. B11 replaces this bean with
 * one that reads {@code verification_applications}.
 */
@Component
class NotStartedVerificationStatuses implements VerificationStatuses {
	
	@Override
	public Map<Role, VerificationStatus> of(User user) {
		Map<Role, VerificationStatus> statuses = new EnumMap<>(Role.class);
		for (Role role : new Role[] {Role.TENANT, Role.LANDLORD}) {
			if (user.hasRole(role)) {
				statuses.put(role, VerificationStatus.DRAFT);
			}
		}
		return statuses;
	}
	
}
