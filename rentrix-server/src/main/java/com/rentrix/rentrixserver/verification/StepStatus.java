package com.rentrix.rentrixserver.verification;

/** State of one onboarding step on {@code GET /me/verification} (D-89). */
public enum StepStatus {
	DONE,
	/** Sent, waiting for an admin check (an ID method or LinkedIn). */
	PENDING,
	/** Required and not done: submit is refused while any step is missing. */
	MISSING,
	/** LinkedIn only: the admin found a mismatch; no badge, never blocks approval (D-100). */
	REJECTED,
	/** Optional and not given. */
	SKIPPED,
	/** Not a step for this role (the rental agreement for landlords). */
	NOT_APPLICABLE
}
