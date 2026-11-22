package com.rentrix.rentrixserver.identity;

/** Why an identity check is {@code REJECTED} or {@code REVOKED} (§5.15). */
public enum IdentityRejectCode {
	ID_UNREADABLE, ID_NAME_MISMATCH, AADHAAR_NOT_MASKED, LINKEDIN_MISMATCH, PHONE_CHANGED, OTHER
}
