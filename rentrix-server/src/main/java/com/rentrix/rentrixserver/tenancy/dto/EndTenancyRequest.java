package com.rentrix.rentrixserver.tenancy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** {@code POST /tenancies/{id}/end} (D-36): the last day, not in the future. */
public record EndTenancyRequest(
	@NotNull LocalDate endDate,
	@Min(0) @Max(TenancyLimits.MAX_DEPOSIT_INR) Long depositRefundedInr
) {
}
