package com.rentrix.rentrixserver.agreement.dto;

import com.rentrix.rentrixserver.agreement.AgreementFlag;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.verification.VerificationState;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * An agreement on the admin console (§12.2 {@code /admin/agreements}, D-136, D-151): the agreement as the parties see
 * it, its admin-only flags, who uploaded it and the tenancy it backs, so the card can be checked and decided in one
 * place. The approvals queue (W9) renders it.
 */
public record AdminAgreementView(
	AgreementView agreement,
	Set<AgreementFlag> flags,
	Person uploader,
	TenancyFacts tenancy
) {
	
	public record Person(UUID id, String displayName) {
	}
	
	/** The tenancy the agreement backs: its home, period, rent, state and green tick. */
	public record TenancyFacts(UUID id, UUID propertyId, String home, String cityName, String unitLabel,
		LocalDate startDate, LocalDate endDate, long initialRentInr, Long depositInr, TenancyStatus status,
		boolean ownerOnPlatform, VerificationState verification) {
	}
	
}
