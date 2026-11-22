package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.common.scheduling.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Nightly delete of phone OTP challenges 24 h after they expired or were used (§5.16), on one instance at a time
 * (D-08). The per-day send limits look back 24 h from creation, so no row they need is deleted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneOtpCleanupJob {
	
	static final String JOB_NAME = "phone-otp-cleanup";
	static final Duration KEEP = Duration.ofHours(24);
	
	private final JobLock jobLock;
	private final PhoneOtpChallengeRepository challenges;
	private final Clock clock;
	
	@Scheduled(cron = "0 41 2 * * *", zone = "UTC")
	public void run() {
		jobLock.runLocked(JOB_NAME, Duration.ofMinutes(10), Duration.ofMinutes(1), this::purge);
	}
	
	/** Deletes finished challenges older than {@link #KEEP}; returns how many. */
	public int purge() {
		int deleted = challenges.deleteFinishedBefore(clock.instant().minus(KEEP));
		if (deleted > 0) {
			log.info("Deleted {} finished phone OTP challenges", deleted);
		}
		return deleted;
	}
	
}
