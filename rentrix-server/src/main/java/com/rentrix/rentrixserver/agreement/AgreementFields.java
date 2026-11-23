package com.rentrix.rentrixserver.agreement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The key fields of a rental agreement as the uploader typed them from the document (manual extractor, D-127):
 * the party names, the address, the money terms, the dates and the stamp or registration number (empty when the
 * agreement has none). Names, rent and start date are needed; the rest helps the team check it. Every text is
 * masked for Aadhaar-like numbers before it is stored ({@link AadhaarMask}, D-131).
 */
public record AgreementFields(
	@NotBlank(message = "Type the owner's name as the agreement shows it.") @Size(max = 120) String landlordName,
	@NotBlank(message = "Type the tenant's name as the agreement shows it.") @Size(max = 120) String tenantName,
	@Size(max = 500) String addressText,
	@Pattern(regexp = "[1-9][0-9]{5}", message = "Enter a 6-digit PIN code.") String pincode,
	@NotNull(message = "Enter the monthly rent from the agreement.") @Positive(message = "Enter the monthly rent from the agreement.")
	@Max(value = 10_000_000, message = "Enter the monthly rent in rupees.") Long monthlyRentInr,
	@PositiveOrZero(message = "Enter the deposit in rupees, or leave it empty.") @Max(100_000_000) Long depositInr,
	@NotNull(message = "Enter the start date from the agreement.") LocalDate startDate,
	LocalDate endDate,
	@Size(max = 64, message = "The stamp or registration number is at most 64 characters.") String anchorNumber,
	LocalDate anchorDate
) {
}
