package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The approval gate on trust writes (D-91, D-105): the caller's application for the side they act on must be
 * {@code APPROVED}, else {@code 403 approval_required} with {@code approval: {role, status}}; and at least one ID
 * method (Aadhaar, PAN or phone) must still be {@code VERIFIED}, else {@code 403 id_verification_required}. Both are
 * read from the database on each call, never from the token, so a decision or a revoked ID takes effect at once.
 * Safety exits (decline, cancel an own request, end a confirmed tenancy, withdraw, report, delete the account) never
 * call it; admins are not gated in their admin actions. Not transactional itself: it joins the caller's
 * transaction, so a refusal never marks that transaction rollback-only on its own.
 */
@Component
@RequiredArgsConstructor
public class ApprovalGuard {
	
	private final VerificationService verification;
	private final IdentityService identity;
	
	public void require(User user, Role role) {
		VerificationStatus status = verification.status(user, role);
		if (status != VerificationStatus.APPROVED) {
			throw ApiException.approvalRequired(role.name(), status.name());
		}
		if (!identity.isIdVerified(user)) {
			throw ApiException.idVerificationRequired();
		}
	}
	
	/** The same check as {@link #require} without throwing. */
	public boolean isApproved(User user, Role role) {
		return verification.status(user, role) == VerificationStatus.APPROVED && identity.isIdVerified(user);
	}
	
}
