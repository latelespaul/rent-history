package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.user.Role;

/**
 * Published when an admin approves, rejects or revokes an application; {@link VerificationMailer} emails the user
 * once the decision has committed (D-65, D-90). Carries copies of what the email needs, not entities.
 */
public record VerificationDecided(String email, String displayName, Role role, VerificationEventType outcome,
	VerificationDecisionCode code, String note) {
}
