package com.rentrix.rentrixserver.common.idempotency;

import com.rentrix.rentrixserver.common.scheduling.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Hourly delete of expired {@code Idempotency-Key} rows, on one instance at a time (D-08). */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {

	static final String JOB_NAME = "idempotency-cleanup";

	private final JobLock jobLock;
	private final IdempotencyStore store;

	@Scheduled(cron = "0 17 * * * *", zone = "UTC")
	public void run() {
		jobLock.runLocked(JOB_NAME, Duration.ofMinutes(10), Duration.ofMinutes(1), () -> {
			int deleted = store.purgeExpired();
			if (deleted > 0) {
				log.info("Deleted {} expired idempotency keys", deleted);
			}
		});
	}

}
