package com.rentrix.rentrixserver.auth.google;

import com.rentrix.rentrixserver.auth.AuthProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test-profile Google verifier (D-75): never calls Google. Accepted shapes:
 * <ul>
 *   <li>{@code fake:<sub>:<email>[:<name>]}: a valid token with a verified email;</li>
 *   <li>{@code fake-unverified:<sub>:<email>}: valid, but {@code email_verified=false};</li>
 *   <li>{@code fake-aud:<aud>:<sub>:<email>}: valid only when {@code aud} is our client id (wrong-audience case).</li>
 * </ul>
 * Anything else is rejected like a bad signature.
 */
@Component
@Profile("test")
public class FakeGoogleTokenVerifier implements GoogleTokenVerifier {
	
	private final String clientId;
	
	public FakeGoogleTokenVerifier(AuthProperties properties) {
		this.clientId = properties.google().clientId();
	}
	
	public static String token(String sub, String email) {
		return "fake:" + sub + ":" + email;
	}
	
	public static String token(String sub, String email, String name) {
		return "fake:" + sub + ":" + email + ":" + name;
	}
	
	@Override
	public GoogleIdentity verify(String idToken) {
		String[] parts = idToken == null ? new String[0] : idToken.split(":", -1);
		if (parts.length >= 3 && parts[0].equals("fake")) {
			return new GoogleIdentity(parts[1], parts[2], true, parts.length > 3 ? parts[3] : null,
				"https://lh3.googleusercontent.com/a/" + parts[1]);
		}
		if (parts.length == 3 && parts[0].equals("fake-unverified")) {
			return new GoogleIdentity(parts[1], parts[2], false, null, null);
		}
		if (parts.length == 4 && parts[0].equals("fake-aud")) {
			if (!parts[1].equals(clientId)) {
				throw new InvalidGoogleTokenException("aud does not match GOOGLE_CLIENT_ID");
			}
			return new GoogleIdentity(parts[2], parts[3], true, null, null);
		}
		throw new InvalidGoogleTokenException("not a fake Google token");
	}
	
}
