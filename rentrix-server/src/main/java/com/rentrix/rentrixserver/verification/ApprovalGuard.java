package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The approval gate on trust writes (D-91): the caller's verification for the side they act on must be
 * {@code APPROVED}, else {@code 403 approval_required} with {@code approval: {role, status}}. It reads the status
 * from the database on each call (through {@link VerificationStatuses}), never from the token, so a decision takes
 * effect at once. Safety exits (decline, cancel an own request, end a confirmed tenancy, withdraw, report, delete
 * the account) never call it.
 * <p>Interim (B5): the statuses come from {@link InterimVerificationStatuses}. PLAN B11 swaps in the real
 * applications and adds the ID-verified half of the gate ({@code 403 id_verification_required}, D-105).
 */
@Component
@RequiredArgsConstructor
public class ApprovalGuard {
	
	private final VerificationStatuses statuses;
	
	public void require(User user, Role role) {
		VerificationStatus status = status(user, role);
		if (status != VerificationStatus.APPROVED) {
			throw ApiException.approvalRequired(role.name(), status.name());
		}
	}
	
	public boolean isApproved(User user, Role role) {
		return status(user, role) == VerificationStatus.APPROVED;
	}
	
	private VerificationStatus status(User user, Role role) {
		return statuses.of(user).getOrDefault(role, VerificationStatus.DRAFT);
	}
	
}
