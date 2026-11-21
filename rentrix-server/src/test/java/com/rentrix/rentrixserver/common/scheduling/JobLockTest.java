package com.rentrix.rentrixserver.common.scheduling;

import com.rentrix.rentrixserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** D-08: a scheduled job runs on one instance at a time, on the real {@code scheduled_job_lock} table. */
@IntegrationTest
class JobLockTest {

	@Autowired
	JobLock jobLock;

	@Autowired
	ScheduledJobLockRepository locks;

	private static String job() {
		return "test-" + UUID.randomUUID().toString().substring(0, 8);
	}

	@Test
	void runsTheJobAndRecordsTheLock() {
		String name = job();
		AtomicInteger runs = new AtomicInteger();
		assertThat(jobLock.runLocked(name, Duration.ofMinutes(5), Duration.ZERO, runs::incrementAndGet)).isTrue();
		assertThat(runs).hasValue(1);
		ScheduledJobLock row = locks.findById(name).orElseThrow();
		assertThat(row.getLockedBy()).isNotBlank();
		assertThat(row.getLockUntil()).isBeforeOrEqualTo(Instant.now().plusSeconds(1));
	}

	@Test
	void skipsWhileAnotherRunHoldsTheLock() {
		String name = job();
		AtomicBoolean innerRan = new AtomicBoolean();
		boolean outer = jobLock.runLocked(name, Duration.ofMinutes(5), Duration.ZERO, () ->
			assertThat(jobLock.runLocked(name, Duration.ofMinutes(5), Duration.ZERO, () -> innerRan.set(true)))
				.isFalse());
		assertThat(outer).isTrue();
		assertThat(innerRan).isFalse();
		// released: the next tick runs again (the row now exists, so this takes the UPDATE path)
		assertThat(jobLock.runLocked(name, Duration.ofMinutes(5), Duration.ZERO, () -> { })).isTrue();
	}

	@Test
	void lockAtLeastForKeepsTheNextTickOut() {
		String name = job();
		assertThat(jobLock.runLocked(name, Duration.ofMinutes(10), Duration.ofMinutes(5), () -> { })).isTrue();
		assertThat(jobLock.runLocked(name, Duration.ofMinutes(10), Duration.ofMinutes(5), () -> { })).isFalse();
	}

	@Test
	void takesOverALockWhoseHolderDied() {
		String name = job();
		ScheduledJobLock stale = new ScheduledJobLock();
		stale.setName(name);
		stale.setLockedAt(Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS));
		stale.setLockUntil(Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS));
		stale.setLockedBy("dead-instance");
		locks.saveAndFlush(stale);
		assertThat(jobLock.runLocked(name, Duration.ofMinutes(5), Duration.ZERO, () -> { })).isTrue();
		assertThat(locks.findById(name).orElseThrow().getLockedBy()).isNotEqualTo("dead-instance");
	}

	@Test
	void releasesTheLockWhenTheJobThrows() {
		String name = job();
		assertThatThrownBy(() -> jobLock.runLocked(name, Duration.ofMinutes(5), Duration.ZERO, () -> {
			throw new IllegalStateException("job failed");
		})).hasMessage("job failed");
		assertThat(jobLock.runLocked(name, Duration.ofMinutes(5), Duration.ZERO, () -> { })).isTrue();
	}

	@Test
	void rejectsLeastLongerThanMost() {
		assertThatThrownBy(() -> jobLock.runLocked(job(), Duration.ofMinutes(1), Duration.ofMinutes(2), () -> { }))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
