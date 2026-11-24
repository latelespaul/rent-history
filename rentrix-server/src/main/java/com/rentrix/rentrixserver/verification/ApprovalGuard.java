package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.springframework.stereotype.Component;

/**
 * The seam every trust write calls before it changes anything (D-132, D-163). Since D-163 (captain 2026-11-24 17:35,
 * superseding the posting gate of D-144 and D-117) it needs <b>no verification at all</b>: a signed-in user (Google,
 * the only login) may add a home, a tenancy, rent records, reviews and responses with no phone, Aadhaar or LinkedIn
 * verified. What they post shows "Not verified yet" until the team gives it the green tick (D-134); verifying a
 * method only upgrades the badge. So {@code 403 id_verification_required} is never returned any more. What still
 * holds is checked elsewhere: the role the caller acts as by each write itself ({@code role_required}, passed here so
 * the call sites read the same), the sign-in by the security filter, the rate limits (D-32, D-35, D-45), reports
 * (D-49) and admin moderation (D-36, D-51). Safety exits (decline, cancel, end, withdraw, report, delete the account)
 * never called it; admins are not gated in their admin actions.
 */
@Component
public class ApprovalGuard {

	public void require(User user, Role role) {
		// Open to every signed-in user (D-163): nothing to refuse here.
	}

}
