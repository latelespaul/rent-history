package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.user.Role;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

class AccessTokenServiceTest {
	
	private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";
	private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
	
	private static AccessTokenService service(String secret, Instant now) {
		return new AccessTokenService(new JwtProperties(secret, Duration.ofMinutes(15)), Clock.fixed(now, ZoneOffset.UTC));
	}
	
	@Test
	void issuedTokenVerifiesWithSubjectAndRoles() {
		UUID id = UUID.randomUUID();
		AuthenticatedUser user = service(SECRET, NOW).verify(service(SECRET, NOW).issue(id, Set.of(Role.LANDLORD,
			Role.TENANT)));
		assertThat(user.publicId()).isEqualTo(id);
		assertThat(user.roles()).containsExactlyInAnyOrder(Role.LANDLORD, Role.TENANT);
	}
	
	@Test
	void expiredTokenIsRejected() {
		String token = service(SECRET, NOW).issue(UUID.randomUUID(), Set.of());
		assertThatThrownBy(() -> service(SECRET, NOW.plus(Duration.ofMinutes(16))).verify(token))
			.isInstanceOf(InvalidTokenException.class);
	}
	
	@Test
	void tokenSignedWithAnotherKeyIsRejected() {
		String token = service(SECRET.replace('u', 'x'), NOW).issue(UUID.randomUUID(), Set.of());
		assertThatThrownBy(() -> service(SECRET, NOW).verify(token)).isInstanceOf(InvalidTokenException.class);
	}
	
}
