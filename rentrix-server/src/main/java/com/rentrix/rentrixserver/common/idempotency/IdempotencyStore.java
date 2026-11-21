package com.rentrix.rentrixserver.common.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Reads and writes {@code idempotency_keys}. Every call is its own short transaction, so a
 * reservation is visible to other instances before the write it guards starts (D-06: the
 * API keeps no state in memory).
 */
@Service
@RequiredArgsConstructor
public class IdempotencyStore {
	
	/** How long a key is remembered (§12.1). */
	public static final Duration RETENTION = Duration.ofHours(24);
	
	private final IdempotencyRecordRepository records;
	private final Clock clock;
	
	/** The live (not expired) record for this caller and key; an expired one is deleted. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<IdempotencyRecord> findLive(String scope, String key) {
		Optional<IdempotencyRecord> found = records.findByScopeAndIdempotencyKey(scope, key);
		if (found.isPresent() && !found.get().getExpiresAt().isAfter(now())) {
			records.delete(found.get());
			records.flush();
			return Optional.empty();
		}
		return found;
	}
	
	/**
	 * Reserves the key for a request that is about to run.
	 *
	 * @throws DataIntegrityViolationException when another request reserved the same key first
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public IdempotencyRecord reserve(String scope, String key, String requestHash) {
		return records.saveAndFlush(new IdempotencyRecord(scope, key, requestHash, now().plus(RETENTION)));
	}
	
	/** Stores the successful response for replay. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(long recordId, int status, String contentType, String body) {
		records.findById(recordId).ifPresent(record -> {
			record.setState(IdempotencyState.COMPLETED);
			record.setResponseStatus(status);
			record.setResponseContentType(contentType);
			record.setResponseBody(body);
		});
	}
	
	/** Forgets a reservation whose request failed, so the client may retry with the same key. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void release(long recordId) {
		records.findById(recordId).ifPresent(records::delete);
	}
	
	/** Deletes every expired key; returns how many. */
	@Transactional
	public int purgeExpired() {
		return records.deleteExpired(now());
	}
	
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}
	
}
