package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.notification.sms.SmsSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Whether phone OTP can run (D-101): it needs an {@link SmsSender} ({@code SMS_BACKEND}) and an
 * {@code ID_HASH_KEY} to hash the code. In {@code prod} without them the method is listed as unavailable and its
 * endpoints answer {@code 503 phone_otp_unavailable}; the API still starts.
 */
@Component
public class PhoneOtpAvailability {
	
	private final SmsSender sender;
	private final boolean hashing;
	
	public PhoneOtpAvailability(ObjectProvider<SmsSender> sender, IdHasher hasher) {
		this.sender = sender.getIfAvailable();
		this.hashing = hasher.available();
	}
	
	public boolean available() {
		return sender != null && hashing;
	}
	
	/** The sender, when phone OTP is available. */
	public Optional<SmsSender> sender() {
		return available() ? Optional.of(sender) : Optional.empty();
	}
	
}
