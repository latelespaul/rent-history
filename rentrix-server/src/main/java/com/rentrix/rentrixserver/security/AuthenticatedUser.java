package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.user.Role;

import java.util.Set;
import java.util.UUID;

/**
 * The caller, as read from a verified access token. Holds only token claims, so no
 * database read is needed to authenticate a request (stateless API, D-06).
 *
 * @param adminSession true for an admin console token (§13.3): its only role is {@code ADMIN}, and it is accepted
 *                     only on an admin host. A main-site token never holds {@code ADMIN} (D-19).
 */
public record AuthenticatedUser(UUID publicId, Set<Role> roles, boolean adminSession) {
	
	public AuthenticatedUser {
		roles = Set.copyOf(roles);
	}
	
	/** A main-site caller. */
	public AuthenticatedUser(UUID publicId, Set<Role> roles) {
		this(publicId, roles, false);
	}
	
	public boolean hasRole(Role role) {
		return roles.contains(role);
	}
	
}
