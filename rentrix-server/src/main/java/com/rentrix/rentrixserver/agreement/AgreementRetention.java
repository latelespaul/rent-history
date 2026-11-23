package com.rentrix.rentrixserver.agreement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * When an agreement's file is deleted (D-131): once the team decided it, {@code AGREEMENT_FILE_RETENTION_DAYS} (30)
 * days after the later of the admin decision and the other party's confirmation of that file, or after the decision
 * alone when no other party confirmed it. While it is undecided, the file goes 90 days after upload, so a file never
 * waits forever for a decision. The masked fields, the hash, the anchor number and the tier stay.
 */
@Component
public class AgreementRetention {
	
	static final Duration UNDECIDED = Duration.ofDays(90);
	
	private final Duration afterDecision;
	
	public AgreementRetention(@Value("${app.agreements.file-retention-days:30}") int days) {
		if (days < 1) {
			throw new IllegalArgumentException("AGREEMENT_FILE_RETENTION_DAYS must be at least 1");
		}
		this.afterDecision = Duration.ofDays(days);
	}
	
	/** The {@code purge_after} for an agreement with these times. */
	public Instant purgeAfter(Instant uploadedAt, Instant decidedAt, Instant counterpartyConfirmedAt) {
		if (decidedAt == null) {
			return uploadedAt.plus(UNDECIDED);
		}
		Instant later = counterpartyConfirmedAt != null && counterpartyConfirmedAt.isAfter(decidedAt)
			? counterpartyConfirmedAt : decidedAt;
		return later.plus(afterDecision);
	}
	
	public void apply(RentalAgreement agreement) {
		agreement.setPurgeAfter(purgeAfter(agreement.getCreatedAt(), agreement.getDecidedAt(),
			agreement.getCounterpartyConfirmedAt()));
	}
	
}
