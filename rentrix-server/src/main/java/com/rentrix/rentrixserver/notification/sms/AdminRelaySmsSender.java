package com.rentrix.rentrixserver.notification.sms;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preview only ({@code SMS_BACKEND=admin-relay}, D-137): sends nothing and keeps each pending code in memory for
 * the OTP lifetime, so an admin can read it in the admin console and pass it to a trusted first user. Nothing is
 * stored in the database beyond the code's HMAC (D-101), and a restart forgets every relayed code. The code is
 * logged only in {@code dev}. The startup guard refuses this sender under {@code prod} unless
 * {@code PREVIEW_MODE=true}, and every start with it logs one warning. Each held code also goes to the optional
 * {@link RelayPush} (Telegram when configured); a push failure never breaks the OTP.
 */
@Slf4j
public class AdminRelaySmsSender implements SmsSender {
	
	private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
	
	/** One code waiting for its user; read by admins only. */
	public record RelayedCode(String phoneE164, String code, Instant sentAt, Instant expiresAt) {
		
		@Override
		public String toString() {
			return "RelayedCode[phone=" + PhoneMasks.mask(phoneE164) + ", code=***, expiresAt=" + expiresAt + "]";
		}
		
	}
	
	private final Map<String, RelayedCode> pending = new ConcurrentHashMap<>();
	private final Duration ttl;
	private final Clock clock;
	private final boolean logCodes;
	private final RelayPush push;
	
	/**
	 * @param ttl      how long a code is kept: the OTP lifetime ({@code OTP_TTL_MINUTES})
	 * @param logCodes true in {@code dev} only: also write the code to the log
	 */
	public AdminRelaySmsSender(Duration ttl, Clock clock, boolean logCodes) {
		this(ttl, clock, logCodes, RelayPush.NONE);
	}
	
	/**
	 * @param push also called with each held code; {@link RelayPush#NONE} for none
	 */
	public AdminRelaySmsSender(Duration ttl, Clock clock, boolean logCodes, RelayPush push) {
		this.ttl = ttl;
		this.clock = clock;
		this.logCodes = logCodes;
		this.push = push;
	}
	
	@Override
	public void send(String phoneE164, String text) {
		Matcher matcher = CODE.matcher(text);
		if (!matcher.find()) {
			throw new SmsSendException("The admin relay found no code in the message.", null);
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		RelayedCode held = new RelayedCode(phoneE164, matcher.group(1), now, now.plus(ttl));
		pending.put(phoneE164, held);
		if (logCodes) {
			log.info("SMS to {} (SMS_BACKEND=admin-relay, not sent, held for admins): {}", PhoneMasks.mask(phoneE164),
				text);
		} else {
			log.info("SMS code for {} held for admins (SMS_BACKEND=admin-relay, not sent)", PhoneMasks.mask(phoneE164));
		}
		try {
			push.push(phoneE164, held.code(), held.expiresAt());
		} catch (RuntimeException e) {
			log.warn("Relay push failed for {} ({})", PhoneMasks.mask(phoneE164), e.getClass().getSimpleName());
		}
	}
	
	/** The codes still valid, newest first; expired ones are dropped. */
	public List<RelayedCode> pending() {
		Instant now = clock.instant();
		pending.values().removeIf(code -> !code.expiresAt().isAfter(now));
		return pending.values().stream().sorted(Comparator.comparing(RelayedCode::sentAt).reversed()).toList();
	}
	
}
