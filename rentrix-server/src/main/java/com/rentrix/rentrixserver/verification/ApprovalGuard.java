package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The gate on trust writes, open by default (D-132, D-117): the caller needs a {@code VERIFIED} phone, else
 * {@code 403 id_verification_required} with {@code missing: ["phone"]}. An admin approval is no longer required:
 * it gives the green tick instead (D-134), so {@code approval_required} is never returned. The role the caller acts
 * as is checked by each write itself ({@code role_required}); it is passed here so the call sites read the same as
 * before. The phone is read from the database on each call, never from the token, so a revoked phone (a phone
 * change, D-101) closes writes at once and a new verified phone opens them with no admin step. Safety exits
 * (decline, cancel an own request, end a confirmed tenancy, withdraw, report, delete the account) never call it;
 * admins are not gated in their admin actions. Not transactional itself: it joins the caller's transaction, so a
 * refusal never marks that transaction rollback-only on its own.
 */
@Component
@RequiredArgsConstructor
public class ApprovalGuard {
	
	private final IdentityService identity;
	
	public void require(User user, Role role) {
		if (!canWrite(user, role)) {
			throw ApiException.idVerificationRequired();
		}
	}
	
	/** The same check as {@link #require} without throwing. */
	public boolean canWrite(User user, Role role) {
		return identity.isPhoneVerified(user);
	}
	
}
