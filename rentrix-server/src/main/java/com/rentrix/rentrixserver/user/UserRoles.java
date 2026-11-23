package com.rentrix.rentrixserver.user;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * The roles the main site sees for a user (D-19): the {@code user_roles} rows without {@code ADMIN}. The admin role
 * is never shown, granted or used on the main site; it exists only in an admin console session on the admin host
 * ({@code AdminConsoleMembers}, §13.3).
 */
@Component
public class UserRoles {
	
	public Set<Role> effective(User user) {
		Set<Role> roles = user.getRoles().isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(user.getRoles());
		roles.remove(Role.ADMIN);
		return roles;
	}
	
}
