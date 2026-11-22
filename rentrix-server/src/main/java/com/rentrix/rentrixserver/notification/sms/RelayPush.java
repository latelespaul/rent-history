package com.rentrix.rentrixserver.notification.sms;

import java.time.Instant;

/**
 * An optional push of a code the {@link AdminRelaySmsSender} holds (D-137), so admins learn about it without
 * opening the admin console. A push failure never breaks the OTP: the sender catches and logs it.
 */
@FunctionalInterface
public interface RelayPush {
	
	/** No push: the code is only held for the admin console. */
	RelayPush NONE = (phoneE164, code, expiresAt) -> {
	};
	
	/**
	 * @param phoneE164 the number the code is for, E.164
	 * @param code      the 6-digit code
	 * @param expiresAt when the code stops working
	 */
	void push(String phoneE164, String code, Instant expiresAt);
	
}
