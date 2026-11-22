package com.rentrix.rentrixserver.verification;

/** Why an application is rejected or an approval revoked (§5.13); shown to the user with the note. */
public enum VerificationDecisionCode {
	NO_VERIFIED_ID, ID_UNREADABLE, ID_NAME_MISMATCH, AADHAAR_NOT_MASKED, DOCUMENT_MISSING, DUPLICATE_ACCOUNT, OTHER
}
