package com.rentrix.rentrixserver.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.user.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of {@code GET /me/verification}: the caller's verification for one held role (§12.2, D-89).
 *
 * @param id       the application's {@code public_id}; null until the first submit
 * @param decision the last decision: for {@code REJECTED} its code and note (shown to the user); for
 *                 {@code APPROVED} only its date (the approve note is for admins only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationView(
	UUID id,
	Role role,
	VerificationStatus status,
	int revision,
	Steps steps,
	Instant submittedAt,
	Decision decision
) {
	
	/**
	 * The stepper's steps (D-120, D-144): the profile and any one verified method ({@code method}: phone, masked
	 * Aadhaar or LinkedIn; {@code PENDING} while only an admin-checked one waits) are required to request
	 * verification. {@code phone}, {@code identity} (PAN or Aadhaar) and {@code linkedin} show each method on its own;
	 * the rental details are optional.
	 */
	public record Steps(StepStatus profile, StepStatus phone, StepStatus method, StepStatus identity,
		StepStatus linkedin, StepStatus rentalAgreement) {
	}
	
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Decision(VerificationDecisionCode code, String note, Instant decidedAt) {
	}
	
}
