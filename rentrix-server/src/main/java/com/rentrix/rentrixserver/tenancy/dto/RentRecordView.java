package com.rentrix.rentrixserver.tenancy.dto;

import com.rentrix.rentrixserver.tenancy.RentRecordStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One rent record of a tenancy, for its parties and admins (D-25). {@code canRespond} is true when the caller is
 * the party who must confirm or dispute it; {@code canEdit} when the caller added it and may change or delete it
 * (D-156). The starting rent (the tenancy's first month) is changed on the tenancy itself.
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
	boolean canEdit,
	Instant createdAt
) {
}
