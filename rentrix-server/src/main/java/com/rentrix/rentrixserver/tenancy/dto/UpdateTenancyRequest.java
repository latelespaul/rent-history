package com.rentrix.rentrixserver.tenancy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code PATCH /tenancies/{id}} (D-156): the creator's corrected period, rent and deposit; the whole set replaces the
 * old values. {@code endDate} null means the tenancy is still current. {@code initialRentInr} may be null only on a
 * tenant's own one-sided record ("Rent not shared"), as on create.
 */
public record UpdateTenancyRequest(
	@NotNull LocalDate startDate,
	LocalDate endDate,
	@Min(1) @Max(TenancyLimits.MAX_RENT_INR) Long initialRentInr,
	@Min(0) @Max(TenancyLimits.MAX_DEPOSIT_INR) Long depositInr
) {
}
