package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's public green tick for one role (D-134): an admin approved their verification application for it.
 * Property pages show it next to the landlord and each reviewer (#44 re-test, captain 2026-11-23).
 */
@Service
@RequiredArgsConstructor
public class GreenTicks {
	
	private final VerificationApplicationRepository applications;
	
	@Transactional(readOnly = true)
	public boolean has(User user, Role role) {
		return applications.findByUserAndRole(user, role)
			.map(application -> application.getStatus() == VerificationStatus.APPROVED)
			.orElse(false);
	}
	
}
