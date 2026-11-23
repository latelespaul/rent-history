package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.security.AdminConsoleProperties;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Who may use the admin console (D-19, §13.3): an active user whose verified Google email is in
 * {@code ADMIN_CONSOLE_EMAILS}, or who holds the {@code ADMIN} row in {@code user_roles} (the database grant; the dev
 * seed's Demo Admin). Checked at every admin sign-in and refresh and at every admin decision, so removing an email
 * ends that admin's console session within one refresh.
 */
@Component
public class AdminConsoleMembers {
	
	private final Set<String> emails;
	
	public AdminConsoleMembers(AdminConsoleProperties properties) {
		this.emails = Set.copyOf(properties.emails());
	}
	
	public boolean isMember(User user) {
		return user.getStatus() == UserStatus.ACTIVE && (user.hasRole(Role.ADMIN) || isListed(user));
	}
	
	/** The verified email is in {@code ADMIN_CONSOLE_EMAILS}. */
	public boolean isListed(User user) {
		return user.getEmailVerifiedAt() != null && isListed(user.getEmail());
	}
	
	/** {@code email} (lower case) is in {@code ADMIN_CONSOLE_EMAILS}. */
	public boolean isListed(String email) {
		return email != null && emails.contains(email);
	}
	
}
