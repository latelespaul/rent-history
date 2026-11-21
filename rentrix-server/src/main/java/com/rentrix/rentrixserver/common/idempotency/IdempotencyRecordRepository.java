package com.rentrix.rentrixserver.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
	
	Optional<IdempotencyRecord> findByScopeAndIdempotencyKey(String scope, String idempotencyKey);
	
	@Modifying
	@Query("delete from IdempotencyRecord r where r.id = :id")
	int deleteRecord(@Param("id") long id);
	
	@Modifying
	@Query("delete from IdempotencyRecord r where r.expiresAt <= :now")
	int deleteExpired(@Param("now") Instant now);
	
}
