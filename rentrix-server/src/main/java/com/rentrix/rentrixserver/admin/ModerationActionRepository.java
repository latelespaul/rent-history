package com.rentrix.rentrixserver.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
	
	/** The audit trail of one record, oldest first (index {@code ix_moderation_actions_target}). */
	List<ModerationAction> findByTargetTypeAndTargetRefOrderByCreatedAtAscIdAsc(ModerationTargetType targetType,
		String targetRef);
	
}
