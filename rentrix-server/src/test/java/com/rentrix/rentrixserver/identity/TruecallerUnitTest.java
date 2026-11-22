package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.VerificationApplicationRepository;
import com.rentrix.rentrixserver.verification.VerificationEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TruecallerUnitTest {

	/** A clock the test moves forward. */
	static final class MovingClock extends Clock {

		Instant now = Instant.parse("2026-11-22T18:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	@Test
	void anExpiredNonceTakesNoToken() {
		MovingClock clock = new MovingClock();
		PhoneVerifier verifier = mock(PhoneVerifier.class);
		UserRepository users = mock(UserRepository.class);
		IdentityService identity = mock(IdentityService.class);
		when(identity.truecallerAvailable()).thenReturn(true);
		UUID userId = UUID.randomUUID();
		when(users.findByPublicId(userId)).thenReturn(Optional.of(new User()));
		TruecallerProperties properties = new TruecallerProperties(true, "key", "Doorway Living", true, null, null);
		TruecallerService service = new TruecallerService(properties, verifier, mock(IdHasher.class), identity,
			mock(IdentityVerificationRepository.class), users, mock(VerificationApplicationRepository.class),
			mock(VerificationEventRepository.class), clock, mock(PlatformTransactionManager.class));

		String requestId = service.start(userId).requestId();
		clock.now = clock.now.plus(properties.requestTtl()).plus(Duration.ofSeconds(1));
		service.callback(requestId, null, FakeTruecallerVerifier.token("919876543210"),
			"https://profile4.truecaller.com/v1/default");

		verify(verifier, never()).fetchPhone(any(), anyString());
		assertThat(service.status(userId, requestId).status()).isEqualTo(TruecallerService.Status.EXPIRED);
		service.stop();
	}

	@Test
	void onlyHttpsTruecallerProfileHostsAreFetched() {
		assertThat(TruecallerService.profileEndpoint("https://profile4.truecaller.com/v1/default")).isPresent();
		assertThat(TruecallerService.profileEndpoint("https://profile4-noneu.truecaller.com/v1/default")).isPresent();
		assertThat(TruecallerService.profileEndpoint("https://PROFILE4-NONEU.truecaller.com/v1/default")).isPresent();
		assertThat(TruecallerService.profileEndpoint("https://truecaller.com/v1/default")).isEmpty();
		assertThat(TruecallerService.profileEndpoint("https://api4.truecaller.com/v1/default")).isEmpty();
		assertThat(TruecallerService.profileEndpoint("https://profile4.truecaller.com.evil.example/v1")).isEmpty();
		assertThat(TruecallerService.profileEndpoint("http://profile4.truecaller.com/v1/default")).isEmpty();
		assertThat(TruecallerService.profileEndpoint(null)).isEmpty();
	}

	@Test
	void readsTheFirstNumberWhetherJsonNumberOrString() {
		TruecallerVerifier real = new TruecallerVerifier(
			new TruecallerProperties(true, "key", null, false, null, null), JsonMapper.builder().build());
		assertThat(real.firstPhone("{\"phoneNumbers\":[919999999999],\"name\":{\"first\":\"A\"}}"))
			.isEqualTo("919999999999");
		assertThat(real.firstPhone("{\"phoneNumbers\":[\"919999999999\"]}")).isEqualTo("919999999999");
		assertThatThrownBy(() -> real.firstPhone("{\"phoneNumbers\":[]}"))
			.isInstanceOf(PhoneVerifier.VerifierException.class);
		assertThatThrownBy(() -> real.firstPhone("<html>"))
			.isInstanceOf(PhoneVerifier.VerifierException.class);
	}

}
