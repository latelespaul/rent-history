package com.rentrix.rentrixserver.agreement;

/** Signals for admins on an agreement (§5.20 {@code flags}); shown to admins only. */
public enum AgreementFlag {
	/** An Aadhaar-like number was typed into a field and masked before it was stored (D-131). */
	AADHAAR_MASKED_ON_INTAKE,
	/** The same file was uploaded for another tenancy (D-129). */
	AGREEMENT_REUSED
}
