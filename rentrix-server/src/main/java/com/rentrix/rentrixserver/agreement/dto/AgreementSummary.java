package com.rentrix.rentrixserver.agreement.dto;

import com.rentrix.rentrixserver.agreement.AgreementStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The live rental agreement of a tenancy as its parties see it on the tenancy (§5.20): enough for "Agreement
 * uploaded · awaiting team check" and the next step. {@code uploadedByMe} tells the uploader from the other party;
 * {@code fileAvailable} is false once retention deleted the file (D-131).
 */
public record AgreementSummary(UUID id, AgreementStatus status, boolean uploadedByMe, boolean fileAvailable,
	Instant uploadedAt) {
}
