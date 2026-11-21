package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.user.Role;

import java.util.Set;
import java.util.UUID;

/**
 * The caller, as read from a verified access token. Holds only token claims, so no
 * database read is needed to authenticate a request (stateless API, D-06).
 */
public record AuthenticatedUser(UUID publicId, Set<Role> roles) {
	
	public AuthenticatedUser {
		roles = Set.copyOf(roles);
	}
	
	public boolean hasRole(Role role) {
		return roles.contains(role);
	}
	
}
