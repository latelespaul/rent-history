package com.rentrix.rentrixserver.identity;

import java.net.URI;

/**
 * Turns a phone-verification provider's access token into the verified phone number, server side. For Truecaller
 * ({@link TruecallerVerifier}) that is {@code GET <endpoint>} with {@code Authorization: Bearer <accessToken>}, and
 * the number is the profile's first {@code phoneNumbers} entry as Truecaller wrote it (for example
 * {@code 919999999999}). Nothing else of the profile is read or kept. The caller has already checked that
 * {@code endpoint} is an HTTPS Truecaller profile host. Dev and test use {@link FakeTruecallerVerifier}.
 */
public interface PhoneVerifier {

	/** @throws VerifierException when the profile cannot be read or has no phone number */
	String fetchPhone(URI endpoint, String accessToken);

	/** The profile could not be fetched or used. The message never holds the token or the number. */
	class VerifierException extends RuntimeException {

		public VerifierException(String message) {
			super(message);
		}

	}

}
