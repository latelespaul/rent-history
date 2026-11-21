package com.rentrix.rentrixserver.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, Long> {
	
	Optional<Report> findByPublicId(UUID publicId);
	
	boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);
	
	Page<Report> findByStatus(ReportStatus status, Pageable pageable);
	
}
