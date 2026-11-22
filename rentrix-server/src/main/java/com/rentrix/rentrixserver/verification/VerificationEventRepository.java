package com.rentrix.rentrixserver.verification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface VerificationEventRepository extends JpaRepository<VerificationEvent, Long> {
	
	List<VerificationEvent> findByApplicationOrderByCreatedAtAscIdAsc(VerificationApplication application);
	
	/** Recent events of one kind, oldest first (the 3 submits / 24 h limit, D-90). */
	List<VerificationEvent> findByApplicationAndEventAndCreatedAtAfterOrderByCreatedAtAsc(
		VerificationApplication application, VerificationEventType event, Instant after);
	
}
