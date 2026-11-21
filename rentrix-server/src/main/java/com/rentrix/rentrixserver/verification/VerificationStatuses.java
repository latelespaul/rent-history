package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;

import java.util.Map;

/**
 * The verification status per held {@code TENANT} / {@code LANDLORD} role, shown on {@code GET /me} as
 * {@code verification: {TENANT: status, LANDLORD: status}} (§12.2, D-89).
 */
public interface VerificationStatuses {
	
	Map<Role, VerificationStatus> of(User user);
	
}
