package com.rentrix.rentrixserver.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Dev and test Truecaller verifier ({@code TRUECALLER_FAKE=true}, refused in any other profile): never calls
 * Truecaller. An access token {@code fake:<phone>} (for example {@code fake:919876543210}) reads as a profile
 * with that phone number; any other token fails like a {@code 401} from Truecaller.
 */
@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "app.truecaller", name = "fake", havingValue = "true")
public class FakeTruecallerVerifier implements PhoneVerifier {

	public static final String PREFIX = "fake:";

	/** The access token the fake reads as a profile with {@code phone}. */
	public static String token(String phone) {
		return PREFIX + phone;
	}

	@Override
	public String fetchPhone(URI endpoint, String accessToken) {
		if (accessToken != null && accessToken.startsWith(PREFIX) && accessToken.length() > PREFIX.length()) {
			return accessToken.substring(PREFIX.length());
		}
		throw new VerifierException("not a fake Truecaller token");
	}

}
