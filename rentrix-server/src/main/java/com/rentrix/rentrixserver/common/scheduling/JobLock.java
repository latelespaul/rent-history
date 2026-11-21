package com.rentrix.rentrixserver.common.scheduling;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Runs a scheduled job on at most one instance at a time (D-08), ShedLock style, on the
 * {@code scheduled_job_lock} table. The lock is a row per job name held until {@code lock_until}:
 * <ul>
 *   <li>take it by moving {@code lock_until} forward when it has passed (or by inserting the row
 *       the first time); if no row changed, another instance holds it and the job is skipped;</li>
 *   <li>{@code lockAtMostFor} is the safety net when an instance dies mid-job;</li>
 *   <li>{@code lockAtLeastFor} keeps instances with slightly different clocks from running the same
 *       tick twice.</li>
 * </ul>
 * Each step is its own short transaction, so the lock is visible to other instances at once.
 */
@Slf4j
@Component
public class JobLock {
	
	private final ScheduledJobLockRepository locks;
	private final EntityManager entityManager;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final String instance;
	
	public JobLock(ScheduledJobLockRepository locks, EntityManager entityManager,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.locks = locks;
		this.entityManager = entityManager;
		this.tx = new TransactionTemplate(transactionManager);
		this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
		this.instance = instanceName();
	}
	
	/**
	 * Runs {@code job} if this instance gets the lock named {@code name}.
	 *
	 * @return true when the job ran, false when another instance holds the lock
	 */
	public boolean runLocked(String name, Duration lockAtMostFor, Duration lockAtLeastFor, Runnable job) {
		if (lockAtLeastFor.compareTo(lockAtMostFor) > 0) {
			throw new IllegalArgumentException("lockAtLeastFor must not exceed lockAtMostFor");
		}
		Instant lockedAt = now();
		if (!acquire(name, lockedAt, lockedAt.plus(lockAtMostFor))) {
			log.debug("Job {} skipped: locked by another instance", name);
			return false;
		}
		try {
			job.run();
			return true;
		} finally {
			Instant releaseAt = later(now(), lockedAt.plus(lockAtLeastFor));
			tx.executeWithoutResult(status -> locks.release(name, instance, lockedAt, releaseAt));
		}
	}
	
	private boolean acquire(String name, Instant now, Instant until) {
		Integer taken = tx.execute(status -> locks.takeIfFree(name, now, until, instance));
		if (taken != null && taken > 0) {
			return true;
		}
		try {
			tx.executeWithoutResult(status -> {
				if (locks.existsById(name)) {
					throw new LockHeld();
				}
				ScheduledJobLock lock = new ScheduledJobLock();
				lock.setName(name);
				lock.setLockedAt(now);
				lock.setLockUntil(until);
				lock.setLockedBy(instance);
				entityManager.persist(lock);
				entityManager.flush();
			});
			return true;
		} catch (RuntimeException heldOrRaced) {
			// LockHeld, or another instance inserted the row first (primary key violation)
			return false;
		}
	}
	
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}
	
	private static Instant later(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}
	
	private static String instanceName() {
		String name = ManagementFactory.getRuntimeMXBean().getName(); // pid@host
		return name.length() > 255 ? name.substring(0, 255) : name;
	}
	
	private static final class LockHeld extends RuntimeException {
		
		LockHeld() {
			super(null, null, false, false);
		}
		
	}
	
}
