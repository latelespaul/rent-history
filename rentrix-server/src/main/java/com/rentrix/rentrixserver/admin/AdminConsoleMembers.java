package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.security.AdminConsoleProperties;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserStatus;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Who may use the admin console (D-19, §13.3): an active user whose verified Google email is in
 * {@code ADMIN_CONSOLE_EMAILS}. Under the {@code dev} (and {@code test}) profile the {@code ADMIN} row in
 * {@code user_roles} counts too, for the dev seed's Demo Admin; in {@code prod} it never does. Checked at every admin
 * sign-in and refresh and at every admin decision, so removing an email ends that admin's console session within one
 * refresh.
 */
@Component
public class AdminConsoleMembers {
	
	private final Set<String> emails;
	
	private final boolean roleRowCounts;
	
	public AdminConsoleMembers(AdminConsoleProperties properties, Environment env) {
		this.emails = Set.copyOf(properties.emails());
		this.roleRowCounts = env.acceptsProfiles(Profiles.of("dev", "test"));
	}
	
	public boolean isMember(User user) {
		return user.getStatus() == UserStatus.ACTIVE && (hasRoleRow(user) || isListed(user));
	}
	
	/** The {@code user_roles} ADMIN row grants membership: only under the {@code dev} or {@code test} profile. */
	public boolean hasRoleRow(User user) {
		return roleRowCounts && user.hasRole(Role.ADMIN);
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
