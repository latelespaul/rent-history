package com.rentrix.rentrixserver.home.dto;

import com.rentrix.rentrixserver.home.HomeRelation;
import com.rentrix.rentrixserver.property.dto.CreatePropertyRequest;
import com.rentrix.rentrixserver.tenancy.dto.TenancyLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code POST /me/homes} (D-138): "Add your home" in one step. Either {@code propertyId} (a published home the user
 * picked from "Is it this one?") or {@code home} (a new one, through the location hierarchy, D-107). With
 * {@code relation} {@code RENT} the tenancy fields are required and {@code owner} is optional; with {@code OWN} only a
 * new home is allowed (claiming an existing one is an admin step) and the tenancy and owner fields must be absent.
 * {@code newCity} is a city not in the list (D-142): it is found by name or created pending admin approval, and
 * {@code home.city} is then ignored (send any placeholder, e.g. {@code "OTHER"}).
 */
public record AddHomeRequest(
	UUID propertyId,
	@Valid CreatePropertyRequest home,
	@NotNull HomeRelation relation,
	LocalDate startDate,
	LocalDate endDate,
	@Min(1) @Max(TenancyLimits.MAX_RENT_INR) Long monthlyRentInr,
	@Min(0) @Max(TenancyLimits.MAX_DEPOSIT_INR) Long depositInr,
	@Valid Owner owner,
	@Valid NewCity newCity
) {
	
	/** A city the user typed because it is not in the list (D-142). */
	public record NewCity(
		@NotBlank @Size(max = 80) String name,
		@NotBlank @Size(max = 80) String state
	) {
	}
	
	/** The owner as the tenant knows them; stored privately for a later claim and invite (D-139). */
	public record Owner(
		@Size(max = 80) String name,
		@Email @Size(max = 320) String email,
		@Size(max = 20) String phone
	) {
	}
	
}
