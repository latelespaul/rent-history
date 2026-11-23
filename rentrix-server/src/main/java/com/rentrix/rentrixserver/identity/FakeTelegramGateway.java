package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.notification.sms.AdminRelaySmsSender;
import com.rentrix.rentrixserver.notification.sms.PhoneMasks;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dev and test Telegram Gateway ({@code TELEGRAM_GATEWAY_FAKE=true}, refused in any other profile): never calls
 * Telegram. A number ending in {@code 0000} fails like a number without Telegram. Every other code is kept for
 * tests and, when {@code SMS_BACKEND=admin-relay} runs, handed to the admin relay, so the e2e stack reads it at
 * {@code GET /admin/sms-relay} as it does an SMS code; without the relay it is written to the dev log.
 */
@Slf4j
public class FakeTelegramGateway implements TelegramGateway {

	/** The fake's stand-in for a number that has no Telegram account. */
	public static final String NO_TELEGRAM_SUFFIX = "0000";

	/** One code the fake "delivered". */
	public record Sent(String requestId, String phoneE164, String code) {

		@Override
		public String toString() {
			return "Sent[requestId=" + requestId + ", code=***]";
		}

	}

	private final List<Sent> sent = new CopyOnWriteArrayList<>();
	private final Map<String, String> reported = new ConcurrentHashMap<>();
	private final AdminRelaySmsSender relay;

	/** @param relay the admin relay to hand codes to, or null */
	public FakeTelegramGateway(AdminRelaySmsSender relay) {
		this.relay = relay;
	}

	@Override
	public String send(String phoneE164, String code, Duration ttl) {
		if (phoneE164.endsWith(NO_TELEGRAM_SUFFIX)) {
			throw new GatewayException("sendVerificationMessage refused: PHONE_NUMBER_NOT_FOUND");
		}
		String requestId = "fake-" + UUID.randomUUID();
		sent.add(new Sent(requestId, phoneE164, code));
		if (relay != null) {
			relay.send(phoneE164, code + " is your Telegram code (fake gateway).");
		} else {
			log.info("Telegram code for {} (TELEGRAM_GATEWAY_FAKE=true, not sent): {}", PhoneMasks.mask(phoneE164), code);
		}
		return requestId;
	}

	@Override
	public void reportEntered(String requestId, String code) {
		reported.put(requestId, code);
	}

	/** The last code sent to {@code phoneE164}, for tests. */
	public String lastCode(String phoneE164) {
		for (int i = sent.size() - 1; i >= 0; i--) {
			if (sent.get(i).phoneE164().equals(phoneE164)) {
				return sent.get(i).code();
			}
		}
		throw new IllegalStateException("no code was sent to that number");
	}

	/** Whether the code entered for {@code requestId} was reported back, for tests. */
	public boolean reported(String requestId, String code) {
		return code.equals(reported.get(requestId));
	}

	/** The requests sent so far, oldest first, for tests. */
	public List<Sent> sent() {
		return List.copyOf(sent);
	}

}
