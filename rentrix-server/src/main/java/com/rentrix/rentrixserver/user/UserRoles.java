package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.auth.AuthProperties;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * The roles a user's access token carries (D-19, D-78): the {@code user_roles} rows, plus {@code ADMIN} while
 * the user's verified email is listed in {@code ADMIN_EMAILS}. The env grant is not written to
 * {@code user_roles}, so removing an email from {@code ADMIN_EMAILS} removes that admin at the next sign-in or
 * refresh, while admins granted in the database keep their row.
 */
@Component
public class UserRoles {
	
	private final Set<String> adminEmails;
	
	public UserRoles(AuthProperties properties) {
		this.adminEmails = Set.copyOf(properties.adminEmails());
	}
	
	public Set<Role> effective(User user) {
		Set<Role> roles = user.getRoles().isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(user.getRoles());
		if (isConfiguredAdmin(user)) {
			roles.add(Role.ADMIN);
		}
		return roles;
	}
	
	boolean isConfiguredAdmin(User user) {
		return user.getEmail() != null && user.getEmailVerifiedAt() != null && adminEmails.contains(user.getEmail());
	}
	
}
