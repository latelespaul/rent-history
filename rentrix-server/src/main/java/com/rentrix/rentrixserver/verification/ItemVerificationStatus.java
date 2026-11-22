package com.rentrix.rentrixserver.verification;

/** The green-tick state of a home, tenancy or review (D-134); a null column reads as {@code UNVERIFIED}. */
public enum ItemVerificationStatus {
	UNVERIFIED, VERIFIED, REJECTED
}
