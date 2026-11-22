package com.rentrix.rentrixserver.tenancy.dto;

import com.rentrix.rentrixserver.tenancy.EvidenceLevel;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A tenancy as its parties and admins see it (§5.4): exact dates and deposit are private to them (D-57).
 * {@code myRole} is the caller's side ({@code null} for an admin who is not a party); {@code tenant} is
 * {@code null} while a landlord invite waits for its invitee; {@code inviteEmail} is shown to the landlord only.
 * {@code currentRentInr} is the latest confirmed rent; {@code expiresAt} is set while confirmation is pending.
 */
public record TenancyView(
	UUID id,
	TenancyPropertyRef property,
	TenancyParty myRole,
	PartyRef landlord,
	PartyRef tenant,
	String inviteEmail,
	LocalDate startDate,
	LocalDate endDate,
	long initialRentInr,
	Long currentRentInr,
	Long depositInr,
	Long depositRefundedInr,
	TenancyStatus status,
	TenancyParty initiatedBy,
	String declineReason,
	EvidenceLevel evidenceLevel,
	Instant createdAt,
	Instant expiresAt,
	Instant confirmedAt,
	Instant endedAt,
	TenancyActions actions
) {
}
