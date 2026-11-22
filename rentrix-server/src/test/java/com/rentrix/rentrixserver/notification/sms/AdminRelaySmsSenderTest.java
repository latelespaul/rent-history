package com.rentrix.rentrixserver.notification.sms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The preview-only sender (D-137): holds codes for admins for the OTP lifetime, logs the code only in dev. */
@ExtendWith(OutputCaptureExtension.class)
class AdminRelaySmsSenderTest {
	
	private static final String PHONE = "+919876543210";
	
	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-22T10:00:00Z"));
	private final Clock clock = new Clock() {
		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}
		
		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
		
		@Override
		public Instant instant() {
			return now.get();
		}
	};
	
	@Test
	void holdsTheCodeForAdminsUntilItExpires() {
		AdminRelaySmsSender relay = new AdminRelaySmsSender(Duration.ofMinutes(10), clock, false);
		relay.send(PHONE, "482913 is your Rentrix verification code.");
		assertThat(relay.pending()).singleElement().satisfies(code -> {
			assertThat(code.phoneE164()).isEqualTo(PHONE);
			assertThat(code.code()).isEqualTo("482913");
			assertThat(code.expiresAt()).isEqualTo(Instant.parse("2026-11-22T10:10:00Z"));
			assertThat(code.toString()).doesNotContain("482913").doesNotContain("9876543210");
		});
		// A new code for the same number replaces the old one.
		relay.send(PHONE, "111222 is your Rentrix verification code.");
		assertThat(relay.pending()).singleElement().extracting(AdminRelaySmsSender.RelayedCode::code)
			.isEqualTo("111222");
		now.set(Instant.parse("2026-11-22T10:10:00Z"));
		assertThat(relay.pending()).isEmpty();
	}
	
	@Test
	void logsTheCodeOnlyInDev(CapturedOutput output) {
		new AdminRelaySmsSender(Duration.ofMinutes(10), clock, false).send(PHONE, "482913 is your code.");
		assertThat(output).contains("held for admins").doesNotContain("482913").doesNotContain("9876543210");
		new AdminRelaySmsSender(Duration.ofMinutes(10), clock, true).send(PHONE, "735100 is your code.");
		assertThat(output).contains("735100").doesNotContain("9876543210");
	}
	
	@Test
	void refusesAMessageWithoutACode() {
		AdminRelaySmsSender relay = new AdminRelaySmsSender(Duration.ofMinutes(10), clock, false);
		assertThatThrownBy(() -> relay.send(PHONE, "Hello")).isInstanceOf(SmsSendException.class);
		assertThat(relay.pending()).isEmpty();
	}
	
}
