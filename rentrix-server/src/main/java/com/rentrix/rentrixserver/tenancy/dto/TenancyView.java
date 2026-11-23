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
 * {@code null} for the landlord until a landlord-started request is confirmed; {@code inviteEmail} is shown masked
 * to the landlord and in full to admins only.
 * {@code currentRentInr} is the latest confirmed rent in effect today and {@code currentRentFrom} the date it applies
 * from (the start date when the rent never changed); both are null until the tenancy is confirmed.
 * {@code expiresAt} is set while confirmation is pending.
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
	/** Null when the tenant did not share the rent; the web shows "Rent not shared". */
	Long initialRentInr,
	Long currentRentInr,
	LocalDate currentRentFrom,
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
	TenancyActions actions,
	/**
	 * True once the caller wrote a review on this tenancy. "Add your home" keeps a "Rate and review" step visible
	 * until then (captain 2026-11-23 14:53).
	 */
	boolean reviewed
) {
}
