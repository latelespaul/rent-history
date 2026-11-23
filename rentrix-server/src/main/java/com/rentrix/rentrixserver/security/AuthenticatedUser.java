package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.user.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The caller, as read from a verified access token. Holds only token claims; the one database read per request is
 * the deleted-account check in {@link JwtAuthenticationFilter} (D-160).
 *
 * @param adminSession true for an admin console token (§13.3): its only role is {@code ADMIN}, and it is accepted
 *                     only on an admin host. A main-site token never holds {@code ADMIN} (D-19).
 * @param authTime     when the sign-in that started this session happened ({@code auth_time}, D-157); refreshes keep
 *                     it. Null for a token without the claim, which counts as an old sign-in.
 */
public record AuthenticatedUser(UUID publicId, Set<Role> roles, boolean adminSession, Instant authTime) {
	
	public AuthenticatedUser {
		roles = Set.copyOf(roles);
	}
	
	/** A main-site caller. */
	public AuthenticatedUser(UUID publicId, Set<Role> roles) {
		this(publicId, roles, false, null);
	}
	
	public boolean hasRole(Role role) {
		return roles.contains(role);
	}
	
}
