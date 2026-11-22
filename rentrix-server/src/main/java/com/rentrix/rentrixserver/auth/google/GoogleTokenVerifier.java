package com.rentrix.rentrixserver.auth.google;

/**
 * Verifies a Google ID token (D-70, D-75). Production uses {@link JwksGoogleTokenVerifier}; the {@code test}
 * profile uses a fake, so tests never call Google.
 */
public interface GoogleTokenVerifier {
	
	/** @throws InvalidGoogleTokenException when the token is malformed, badly signed, expired or not for us */
	GoogleIdentity verify(String idToken);
	
}
