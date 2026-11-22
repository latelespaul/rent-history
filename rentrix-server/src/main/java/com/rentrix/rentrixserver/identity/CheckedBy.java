package com.rentrix.rentrixserver.identity;

/** Who decided an identity check (§5.15, D-104). */
public enum CheckedBy {
	/** An admin compared the upload or the profile. */
	ADMIN,
	/** The user confirmed an SMS code. */
	OTP,
	/** A licensed KYC provider (later, D-104). */
	PROVIDER
}
