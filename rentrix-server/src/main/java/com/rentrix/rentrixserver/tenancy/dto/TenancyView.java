package com.rentrix.rentrixserver.tenancy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.tenancy.EvidenceLevel;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.verification.VerificationState;

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
 * {@code verificationStatus} is the team's green tick on the tenancy (D-134, D-147); an edit by its creator takes it
 * away until the team checks it again (D-156). {@code accessToken} is set only when an edit gave the caller a new role
 * (a tenant who switches their record to "I own it" becomes a landlord too, D-16).
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
	 * True once the caller, as the tenant, wrote a review on this tenancy (always false for the landlord side). "Add your home" keeps a "Rate and review" step visible
	 * until then (captain 2026-11-23 14:53).
	 */
	boolean reviewed,
	VerificationState verificationStatus,
	@JsonInclude(JsonInclude.Include.NON_NULL) String accessToken
) {
	
	public TenancyView withAccessToken(String token) {
		return new TenancyView(id, property, myRole, landlord, tenant, inviteEmail, startDate, endDate, initialRentInr,
			currentRentInr, currentRentFrom, depositInr, depositRefundedInr, status, initiatedBy, declineReason,
			evidenceLevel, createdAt, expiresAt, confirmedAt, endedAt, actions, reviewed, verificationStatus, token);
	}
	
}
