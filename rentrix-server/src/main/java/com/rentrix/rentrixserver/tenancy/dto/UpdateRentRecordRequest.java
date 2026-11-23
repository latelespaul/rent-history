package com.rentrix.rentrixserver.tenancy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code PATCH /rent-records/{id}} (D-156): the corrected month, rent and maintenance of a record the caller added;
 * {@code effectiveFrom} is stored as the first day of its month.
 */
public record UpdateRentRecordRequest(
	@NotNull LocalDate effectiveFrom,
	@NotNull @Min(1) @Max(TenancyLimits.MAX_RENT_INR) Long monthlyRentInr,
	@Min(0) @Max(TenancyLimits.MAX_RENT_INR) Long maintenanceInr
) {
}
