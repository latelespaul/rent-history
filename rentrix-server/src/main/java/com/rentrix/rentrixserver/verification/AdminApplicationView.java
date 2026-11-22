package com.rentrix.rentrixserver.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.user.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** An application as an admin sees it after a decision (§12.2 admin). Flags and the approve note are admin-only. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminApplicationView(
	UUID id,
	UUID userId,
	Role role,
	VerificationStatus status,
	int revision,
	Set<String> flags,
	Instant submittedAt,
	Instant decidedAt,
	UUID decidedBy,
	VerificationDecisionCode decisionCode,
	String decisionNote
) {
	
	static AdminApplicationView of(VerificationApplication a) {
		return new AdminApplicationView(a.getPublicId(), a.getUser().getPublicId(), a.getRole(), a.getStatus(),
			a.getRevision(), a.flagSet(), a.getSubmittedAt(), a.getDecidedAt(),
			a.getDecidedBy() == null ? null : a.getDecidedBy().getPublicId(), a.getDecisionCode(), a.getDecisionNote());
	}
	
}
