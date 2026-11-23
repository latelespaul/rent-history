package com.rentrix.rentrixserver.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
	
	/** The audit trail of one record, oldest first (index {@code ix_moderation_actions_target}). */
	List<ModerationAction> findByTargetTypeAndTargetRefOrderByCreatedAtAscIdAsc(ModerationTargetType targetType,
		String targetRef);
	
	/** The audit log page of one action (admin console Audit log, D-159). */
	Page<ModerationAction> findByAction(ModerationActionType action, Pageable pageable);
	
}
