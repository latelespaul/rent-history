package com.rentrix.rentrixserver.rating;

import com.rentrix.rentrixserver.common.scheduling.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Nightly at 02:40 UTC, on one instance (D-08): rebuilds every stored rating aggregate from source reviews and
 * refreshes the Bayesian scores with the day's prior mean (D-33, D-53).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingRepairJob {
	
	static final String JOB_NAME = "rating-repair";
	
	private final JobLock jobLock;
	private final RatingRecomputer recomputer;
	
	@Scheduled(cron = "0 40 2 * * *", zone = "UTC")
	public void run() {
		jobLock.runLocked(JOB_NAME, Duration.ofMinutes(30), Duration.ofMinutes(1), () -> {
			int rows = recomputer.recomputeAll();
			log.info("Rating repair rebuilt {} aggregates", rows);
		});
	}
	
}
