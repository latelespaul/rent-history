package com.rentrix.rentrixserver.tenancy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code POST /tenancies/{id}/rent-records} (D-25): a rent change from a month on. {@code effectiveFrom} may be any
 * day; it is stored as the first day of its month.
 */
public record CreateRentRecordRequest(
	@NotNull LocalDate effectiveFrom,
	@NotNull @Min(1) @Max(TenancyLimits.MAX_RENT_INR) Long monthlyRentInr,
	@Min(0) @Max(TenancyLimits.MAX_RENT_INR) Long maintenanceInr
) {
}
