package com.rentrix.rentrixserver.tenancy;

import com.rentrix.rentrixserver.common.scheduling.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Hourly: pending tenancy requests older than 14 days become {@code EXPIRED} (§6), on one instance (D-08). */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenancyExpiryJob {
	
	static final String JOB_NAME = "tenancy-expiry";
	
	private final JobLock jobLock;
	private final TenancyService tenancies;
	
	@Scheduled(cron = "0 23 * * * *", zone = "UTC")
	public void run() {
		jobLock.runLocked(JOB_NAME, Duration.ofMinutes(10), Duration.ofMinutes(1), () -> {
			int expired = tenancies.expirePending();
			if (expired > 0) {
				log.info("Expired {} pending tenancy requests", expired);
			}
		});
	}
	
}
