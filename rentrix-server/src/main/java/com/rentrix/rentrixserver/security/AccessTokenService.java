package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Issues and verifies access tokens (D-62): HS256 JWT with {@code sub} (user public id),
 * {@code roles}, {@code iat}, {@code exp} and {@code jti}. The sign-in endpoints that hand
 * tokens out come in the auth PR; this class is the shared codec.
 */
@Service
public class AccessTokenService {
	
	static final String ROLES_CLAIM = "roles";
	
	private final SecretKey key;
	private final JwtProperties properties;
	private final Clock clock;
	
	public AccessTokenService(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}
	
	public String issue(UUID userPublicId, Collection<Role> roles) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(userPublicId.toString())
			.claim(ROLES_CLAIM, roles.stream().map(Role::name).sorted().toList())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(properties.accessTokenTtl())))
			.id(UUID.randomUUID().toString())
			.signWith(key, Jwts.SIG.HS256)
			.compact();
	}
	
	public AuthenticatedUser verify(String token) {
		try {
			Claims claims = Jwts.parser()
				.verifyWith(key)
				.clock(() -> Date.from(clock.instant()))
				.build()
				.parseSignedClaims(token)
				.getPayload();
			return new AuthenticatedUser(UUID.fromString(claims.getSubject()), roles(claims.get(ROLES_CLAIM)));
		} catch (JwtException | IllegalArgumentException e) {
			throw new InvalidTokenException("Access token is invalid or expired", e);
		}
	}
	
	private static Set<Role> roles(Object claim) {
		Set<Role> roles = EnumSet.noneOf(Role.class);
		if (claim instanceof List<?> list) {
			for (Object value : list) {
				roles.add(Role.valueOf(String.valueOf(value)));
			}
		}
		return roles;
	}
	
}
