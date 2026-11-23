package com.rentrix.rentrixserver.tenancy.dto;

import com.rentrix.rentrixserver.home.HomeRelation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code PATCH /tenancies/{id}} (D-156): the creator's corrected period, rent and deposit; the whole set replaces the
 * old values. {@code endDate} null means the tenancy is still current. {@code initialRentInr} may be null only on a
 * tenant's own one-sided record ("Rent not shared"), as on create. {@code relation} is the role picked at add-home;
 * {@code OWN} on a tenant's own record of a home they added makes the home theirs and cancels the tenancy, and the
 * other fields are then ignored. Left out, the role stays.
 */
public record UpdateTenancyRequest(
	@NotNull LocalDate startDate,
	LocalDate endDate,
	@Min(1) @Max(TenancyLimits.MAX_RENT_INR) Long initialRentInr,
	@Min(0) @Max(TenancyLimits.MAX_DEPOSIT_INR) Long depositInr,
	HomeRelation relation
) {
}
