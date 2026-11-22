package com.rentrix.rentrixserver.auth.google;

import com.rentrix.rentrixserver.auth.AuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

/**
 * Verifies Google ID tokens server-side (D-70): RS256 signature against Google's JWKS (fetched lazily on the
 * first sign-in and cached by Nimbus, refreshed when an unknown key id appears), {@code iss} is Google,
 * {@code aud} is {@code GOOGLE_CLIENT_ID}, {@code exp} not passed with 60 s clock skew. The
 * {@code email_verified} check is done by the caller on {@link GoogleIdentity#emailVerified()}.
 * Not created in the {@code test} profile, which uses a fake (D-75).
 */
@Component
@Profile("!test")
public class JwksGoogleTokenVerifier implements GoogleTokenVerifier {
	
	static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
	static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
	static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
	
	/** Null when no client id is configured; then every token is refused. */
	private final NimbusJwtDecoder decoder;
	
	@Autowired
	public JwksGoogleTokenVerifier(AuthProperties properties, Clock clock) {
		this(properties.googleEnabled()
			? NimbusJwtDecoder.withJwkSetUri(JWKS_URI).jwsAlgorithm(SignatureAlgorithm.RS256).build()
			: null, properties.google().clientId(), clock);
	}
	
	/** For tests: any decoder (e.g. one built from a local public key) gets the Google validators. */
	JwksGoogleTokenVerifier(NimbusJwtDecoder decoder, String clientId, Clock clock) {
		this.decoder = decoder;
		if (decoder != null) {
			decoder.setJwtValidator(validator(clientId, clock));
		}
	}
	
	static OAuth2TokenValidator<Jwt> validator(String clientId, Clock clock) {
		JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
		timestamps.setClock(clock);
		timestamps.setAllowEmptyExpiryClaim(false);
		JwtClaimValidator<Object> issuer = new JwtClaimValidator<>(JwtClaimNames.ISS,
			iss -> iss != null && ISSUERS.contains(iss.toString()));
		return new DelegatingOAuth2TokenValidator<>(timestamps, issuer, new JwtAudienceValidator(clientId));
	}
	
	@Override
	public GoogleIdentity verify(String idToken) {
		if (decoder == null) {
			throw new InvalidGoogleTokenException("Google sign-in is not configured");
		}
		Jwt jwt;
		try {
			jwt = decoder.decode(idToken);
		} catch (JwtException e) {
			throw new InvalidGoogleTokenException("Google ID token rejected", e);
		}
		String sub = jwt.getSubject();
		if (!StringUtils.hasText(sub)) {
			throw new InvalidGoogleTokenException("Google ID token has no subject");
		}
		Object verified = jwt.getClaims().get("email_verified");
		return new GoogleIdentity(sub, jwt.getClaimAsString("email"),
			Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)),
			jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
	}
	
}
