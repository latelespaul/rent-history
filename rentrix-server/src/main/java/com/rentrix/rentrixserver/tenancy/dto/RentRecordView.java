package com.rentrix.rentrixserver.tenancy.dto;

import com.rentrix.rentrixserver.tenancy.RentRecordStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One rent record of a tenancy, for its parties and admins (D-25). {@code canRespond} is true when the caller is
 * the party who must confirm or dispute it.
 */
public record RentRecordView(
	UUID id,
	LocalDate effectiveFrom,
	long monthlyRentInr,
	Long maintenanceInr,
	RentRecordStatus status,
	PartyRef addedBy,
	boolean addedByMe,
	boolean canRespond,
	Instant createdAt
) {
}
