package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.common.persistence.TimestampedEntity;
import com.rentrix.rentrixserver.common.scheduling.JobLock;
import com.rentrix.rentrixserver.storage.DocumentStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Hourly delete of rental-agreement files whose {@code purge_after} passed (D-131), on one instance at a time (D-08).
 * The row keeps the masked fields, the hash, the anchor number and the tier; only the file and its key go. Each batch
 * is its own transaction, and the file is deleted before its key is cleared, so a crash leaves the key behind for the
 * next run, never a file nobody can find.
 */
@Slf4j
@Component
public class AgreementRetentionJob {
	
	static final String JOB_NAME = "agreement-file-retention";
	static final int BATCH = 100;
	
	private final JobLock jobLock;
	private final RentalAgreementRepository agreements;
	private final DocumentStorage documents;
	private final TransactionTemplate tx;
	private final Clock clock;
	
	public AgreementRetentionJob(JobLock jobLock, RentalAgreementRepository agreements, DocumentStorage documents,
		PlatformTransactionManager transactionManager, Clock clock) {
		this.jobLock = jobLock;
		this.agreements = agreements;
		this.documents = documents;
		this.tx = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}
	
	@Scheduled(cron = "0 43 * * * *", zone = "UTC")
	public void run() {
		jobLock.runLocked(JOB_NAME, Duration.ofMinutes(30), Duration.ofMinutes(1), this::purge);
	}
	
	/** Deletes every due file; returns how many. */
	public int purge() {
		int total = 0;
		while (true) {
			Integer done = tx.execute(status -> {
				List<RentalAgreement> due = agreements.findFilesToPurge(clock.instant(), PageRequest.of(0, BATCH));
				for (RentalAgreement a : due) {
					documents.delete(a.getStorageKey());
					a.setStorageKey(null);
					a.setFileDeletedAt(TimestampedEntity.now());
				}
				return due.size();
			});
			int count = done == null ? 0 : done;
			total += count;
			if (count < BATCH) {
				break;
			}
		}
		if (total > 0) {
			log.info("Deleted {} rental agreement files after their retention time (D-131)", total);
		}
		return total;
	}
	
}
