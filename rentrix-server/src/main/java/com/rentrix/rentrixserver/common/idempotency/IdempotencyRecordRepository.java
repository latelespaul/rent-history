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
	@Query("update IdempotencyRecord r set r.state = :state, r.responseStatus = :status,"
		+ " r.responseContentType = :contentType, r.responseLocation = :location, r.responseBody = :body,"
		+ " r.updatedAt = :now where r.id = :id")
	int completeRecord(@Param("id") long id, @Param("state") IdempotencyState state, @Param("status") int status,
		@Param("contentType") String contentType, @Param("location") String location, @Param("body") String body,
		@Param("now") Instant now);

	@Modifying
	@Query("delete from IdempotencyRecord r where r.expiresAt <= :now")
	int deleteExpired(@Param("now") Instant now);

}
