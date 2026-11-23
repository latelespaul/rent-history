package com.rentrix.rentrixserver.agreement.dto;

import com.rentrix.rentrixserver.agreement.AgreementFields;
import com.rentrix.rentrixserver.agreement.AgreementStatus;
import com.rentrix.rentrixserver.agreement.AgreementTier;
import com.rentrix.rentrixserver.agreement.AnchorKind;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.verification.VerificationState;

import java.time.Instant;
import java.util.UUID;

/**
 * A rental agreement as the two parties of its tenancy see it (§12.2 {@code /me/agreements/{id}}): the typed fields
 * (masked, D-131), the file's type, size and SHA-256 (what both sides confirm, D-128), and where the team's check is.
 * {@code aadhaarMasked} says an Aadhaar-like number was masked in the fields. {@code fileDeletesAfter} is when
 * retention deletes the file; {@code tenancyVerification} and {@code decisionReason} are the team's decision on the
 * tenancy (the green tick, D-134, D-147). Never public.
 */
public record AgreementView(
	UUID id,
	UUID tenancyId,
	AgreementStatus status,
	AgreementTier tier,
	TenancyParty uploaderSide,
	boolean uploadedByMe,
	AnchorKind anchorKind,
	AgreementFields fields,
	boolean aadhaarMasked,
	String contentType,
	long sizeBytes,
	String sha256,
	boolean fileAvailable,
	Instant uploadedAt,
	Instant confirmedAt,
	Instant decidedAt,
	Instant fileDeletesAfter,
	Instant fileDeletedAt,
	VerificationState tenancyVerification,
	String decisionReason
) {
}
