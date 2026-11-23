package com.rentrix.rentrixserver.agreement;

/**
 * Where an uploaded agreement is (§5.20): {@code DRAFT} (uploaded, fields not yet confirmed by the uploader),
 * {@code SUBMITTED} (confirmed, waiting for the team's check), {@code DECIDED} (an admin verified or rejected the
 * tenancy from it) or {@code REPLACED} (a newer upload took its place; its file is deleted at once).
 */
public enum AgreementStatus {
	DRAFT, SUBMITTED, DECIDED, REPLACED
}
