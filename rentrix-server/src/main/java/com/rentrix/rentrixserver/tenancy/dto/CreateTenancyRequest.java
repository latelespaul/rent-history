package com.rentrix.rentrixserver.tenancy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code POST /tenancies} (§12.2). The caller's side follows from the property: its landlord invites a tenant by
 * {@code counterpartyEmail}; anyone else asks the landlord to confirm and sends no email. {@code endDate} only for
 * a tenancy that has already ended (it becomes {@code ENDED} on confirmation, D-34).
 */
public record CreateTenancyRequest(
	@NotNull UUID propertyId,
	@Email @Size(max = 320) String counterpartyEmail,
	@NotNull LocalDate startDate,
	LocalDate endDate,
	/** Required, except on a tenant's own "Add your home" record, where it may be left out (not shared). */
	@Min(1) @Max(TenancyLimits.MAX_RENT_INR) Long initialRentInr,
	@Min(0) @Max(TenancyLimits.MAX_DEPOSIT_INR) Long depositInr
) {
}
