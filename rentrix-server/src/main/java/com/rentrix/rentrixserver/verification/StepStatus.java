package com.rentrix.rentrixserver.verification;

/** State of one onboarding step on {@code GET /me/verification} (D-89). */
public enum StepStatus {
	DONE,
	/** Sent, waiting for an admin check (an ID method or LinkedIn). */
	PENDING,
	/** Required and not done: submit is refused while any step is missing. */
	MISSING,
	/** The admin rejected it (LinkedIn, PAN or Aadhaar): no badge, never blocks posting or approval (D-100). */
	REJECTED,
	/** Optional and not given. */
	SKIPPED,
	/** Not a step for this role (unused since design v4: every step applies to both roles, D-120). */
	NOT_APPLICABLE
}
