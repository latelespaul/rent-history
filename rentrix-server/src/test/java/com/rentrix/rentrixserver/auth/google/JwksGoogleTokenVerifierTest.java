package com.rentrix.rentrixserver.auth.google;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Consumer;

/**
 * The production verifier's checks (D-70) against a locally generated RSA key, so no call reaches Google:
 * signature, issuer, audience and expiry with 60 s skew.
 */
class JwksGoogleTokenVerifierTest {
	
	private static final String CLIENT_ID = "123-test.apps.googleusercontent.com";
	private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
	private static final KeyPair KEY = rsa();
	private static final KeyPair OTHER_KEY = rsa();
	
	private static KeyPair rsa() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
	
	private static JwksGoogleTokenVerifier verifier() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
		return new JwksGoogleTokenVerifier(decoder, CLIENT_ID, Clock.fixed(NOW, ZoneOffset.UTC));
	}
	
	private static String token(KeyPair key, Consumer<JWTClaimsSet.Builder> change) throws Exception {
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
			.issuer("https://accounts.google.com")
			.audience(CLIENT_ID)
			.subject("1098765")
			.issueTime(Date.from(NOW.minusSeconds(60)))
			.expirationTime(Date.from(NOW.plusSeconds(3000)))
			.claim("email", "asha@example.test")
			.claim("email_verified", true)
			.claim("name", "Asha Rao")
			.claim("picture", "https://lh3.googleusercontent.com/a/x");
		change.accept(claims);
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
		jwt.sign(new RSASSASigner(key.getPrivate()));
		return jwt.serialize();
	}
	
	@Test
	void acceptsAValidGoogleToken() throws Exception {
		GoogleIdentity identity = verifier().verify(token(KEY, c -> { }));
		assertThat(identity).isEqualTo(new GoogleIdentity("1098765", "asha@example.test", true, "Asha Rao",
			"https://lh3.googleusercontent.com/a/x"));
		assertThat(verifier().verify(token(KEY, c -> c.issuer("accounts.google.com"))).sub()).isEqualTo("1098765");
	}
	
	@Test
	void reportsUnverifiedEmailForTheCallerToRefuse() throws Exception {
		assertThat(verifier().verify(token(KEY, c -> c.claim("email_verified", false))).emailVerified()).isFalse();
		assertThat(verifier().verify(token(KEY, c -> c.claim("email_verified", "true"))).emailVerified()).isTrue();
	}
	
	@Test
	void rejectsWrongAudienceIssuerSignatureAndExpiry() throws Exception {
		JwksGoogleTokenVerifier verifier = verifier();
		for (String bad : new String[] {
			token(KEY, c -> c.audience("someone-else.apps.googleusercontent.com")),
			token(KEY, c -> c.issuer("https://evil.example.test")),
			token(OTHER_KEY, c -> { }),
			token(KEY, c -> c.expirationTime(Date.from(NOW.minusSeconds(61)))),
			token(KEY, c -> c.expirationTime(null)),
			token(KEY, c -> c.subject(null)),
			"not.a.jwt",
		}) {
			assertThatThrownBy(() -> verifier.verify(bad)).isInstanceOf(InvalidGoogleTokenException.class);
		}
	}
	
	@Test
	void allowsSixtySecondsOfClockSkew() throws Exception {
		assertThat(verifier().verify(token(KEY, c -> c.expirationTime(Date.from(NOW.minusSeconds(59))))).sub())
			.isEqualTo("1098765");
	}
	
	@Test
	void refusesEverythingWithoutAClientId() throws Exception {
		JwksGoogleTokenVerifier off = new JwksGoogleTokenVerifier(null, null, Clock.systemUTC());
		String valid = token(KEY, c -> { });
		assertThatThrownBy(() -> off.verify(valid)).isInstanceOf(InvalidGoogleTokenException.class);
	}
	
}
