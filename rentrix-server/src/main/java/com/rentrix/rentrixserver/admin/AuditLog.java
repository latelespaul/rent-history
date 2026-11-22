package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the append-only admin audit log (D-87): who ({@code admin}), when ({@code created_at}), what
 * ({@code action}, target) and why ({@code reason}). Joins the caller's transaction, so a decision and its audit
 * row commit or roll back together.
 */
@Component
@RequiredArgsConstructor
public class AuditLog {
	
	private final ModerationActionRepository actions;
	
	/**
	 * @param targetId  internal id for joins; null for targets without one (cities)
	 * @param targetRef the target's {@code public_id}, or the city code for {@code CITY}
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ModerationAction record(User admin, ModerationActionType action, ModerationTargetType targetType,
		Long targetId, String targetRef, String reason) {
		ModerationAction row = new ModerationAction();
		row.setAdmin(admin);
		row.setAction(action);
		row.setTargetType(targetType);
		row.setTargetId(targetId);
		row.setTargetRef(targetRef);
		row.setReason(reason);
		return actions.save(row);
	}
	
}
