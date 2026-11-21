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
	/** An IN_PROGRESS reservation older than this was abandoned by a dead instance and may be taken over. */
	public static final Duration IN_PROGRESS_LEASE = Duration.ofMinutes(5);
	
	private final IdempotencyRecordRepository records;
	private final Clock clock;
	
	/**
	 * The live record for this caller and key. An expired one, or an IN_PROGRESS one older than
	 * {@link #IN_PROGRESS_LEASE}, is deleted.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<IdempotencyRecord> findLive(String scope, String key) {
		Optional<IdempotencyRecord> found = records.findByScopeAndIdempotencyKey(scope, key);
		if (found.isPresent() && isDead(found.get(), now())) {
			records.deleteRecord(found.get().getId());
			return Optional.empty();
		}
		return found;
	}
	
	private static boolean isDead(IdempotencyRecord record, Instant now) {
		return !record.getExpiresAt().isAfter(now)
			|| record.getState() == IdempotencyState.IN_PROGRESS
			&& !record.getCreatedAt().plus(IN_PROGRESS_LEASE).isAfter(now);
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
	
	/**
	 * Stores the successful response for replay. One UPDATE with no read before it: on SQLite a
	 * read-then-write transaction fails at once with SQLITE_BUSY when another connection wrote in
	 * between, while a write-only one waits for the busy timeout.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(long recordId, int status, String contentType, String location, String body) {
		records.completeRecord(recordId, IdempotencyState.COMPLETED, status, contentType, location, body, now());
	}
	
	/** Forgets a reservation whose request failed, so the client may retry with the same key. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void release(long recordId) {
		records.deleteRecord(recordId);
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
