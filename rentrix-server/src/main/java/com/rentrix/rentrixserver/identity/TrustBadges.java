package com.rentrix.rentrixserver.identity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The three public verification badges of a reviewer (D-168), shown on each review and person card instead of the
 * person's green tick. {@code google}: the account signs in with Google. {@code id}: any one of phone, masked Aadhaar
 * or LinkedIn is verified ({@link IdentityService#POSTING_METHODS}). {@code agreement}: a rental agreement backs the
 * tenancy ({@code tenancies.evidence_level = DOCUMENT}); present only when that status exists, since the agreement
 * check is not built yet. Yes/no only: never which method or document (D-57).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrustBadges(boolean google, boolean id, Boolean agreement) {
	
	public static final TrustBadges NONE = new TrustBadges(false, false, null);
	
}
