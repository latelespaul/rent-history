package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.report.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
	
	Page<ModerationAction> findByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId, Pageable pageable);
	
}
