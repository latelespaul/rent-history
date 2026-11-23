package com.rentrix.rentrixserver.agreement.dto;

import com.rentrix.rentrixserver.agreement.AgreementFields;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The JSON {@code data} part of {@code POST /me/agreements} (D-123, D-131): the tenancy it backs, the explicit
 * consent to store the file encrypted and show it only to the other party and admins, and the typed fields.
 */
public record UploadAgreementRequest(
	@NotNull(message = "Pick the tenancy this agreement is for.") UUID tenancyId,
	@NotNull(message = "Tick the box to agree before you upload.")
	@AssertTrue(message = "Tick the box to agree before you upload.") Boolean consent,
	@NotNull(message = "Type the agreement's details.") @Valid AgreementFields fields
) {
}
