package com.rentrix.rentrixserver.identity;

import java.util.Optional;
import java.util.Set;

/**
 * Who checks an identity method that is not confirmed by the user alone (D-104). v1 has only
 * {@link AdminReviewProvider} (the admin queue); a later PR may add a licensed KYC provider that returns only a
 * name match, the last 4 and a reference id, keeping the stored data in the same masked shape. No provider is
 * called in tests.
 */
public interface IdentityCheckProvider {
	
	/** The methods this provider can check. */
	Set<IdentityMethod> methods();
	
	/**
	 * Starts a check on {@code check}, which already holds the masked input ({@code last4}, {@code valueHmac},
	 * {@code linkedinUrl}), and leaves it {@code PENDING}.
	 *
	 * @return the reference to follow the check with
	 */
	String start(IdentityVerification check);
	
	/** The current state of a check started with {@link #start}, or empty if the reference is unknown. */
	Optional<IdentityStatus> result(String ref);
	
}
