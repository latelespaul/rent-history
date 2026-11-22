package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.scheduling.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Hourly, on one instance (D-08): person reviews that waited 14 days for their counter-review, or whose review
 * window closed, publish (D-46).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRevealJob {
	
	static final String JOB_NAME = "review-reveal";
	
	private final JobLock jobLock;
	private final ReviewService reviews;
	
	@Scheduled(cron = "0 17 * * * *", zone = "UTC")
	public void run() {
		jobLock.runLocked(JOB_NAME, Duration.ofMinutes(10), Duration.ofMinutes(1), () -> {
			int published = reviews.revealDue();
			if (published > 0) {
				log.info("Revealed {} reviews", published);
			}
		});
	}
	
}
