package com.rentrix.rentrixserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Identity settings (§17, D-101, D-106). {@code hashKey} comes from {@code ID_HASH_KEY} (dev and test ship a
 * built-in one); {@code otpTtl} from {@code OTP_TTL_MINUTES}; {@code otpTestNumberPrefix} from
 * {@code OTP_TEST_NUMBER_PREFIX}.
 *
 * @param otpTestNumberPrefix the E.164 start of the test phone numbers (default {@value #DEFAULT_TEST_PREFIX}, i.e.
 *                            +91 99999 0xxxx): their codes are held for the admin relay page and the log only, and are
 *                            never sent by SMS, the Telegram Gateway or the relay's Telegram push (firstmate
 *                            2026-11-23). Every e2e and release-gate run uses only these numbers.
 */
@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(String hashKey, Duration otpTtl, String otpTestNumberPrefix) {
	
	public static final String DEFAULT_TEST_PREFIX = "+91999990";
	
	private static final Pattern SEPARATORS = Pattern.compile("[\\s().-]");
	
	private static final Pattern TEST_PREFIX = Pattern.compile("\\+91[0-9]{1,9}");
	
	@ConstructorBinding
	public IdentityProperties {
		hashKey = StringUtils.hasText(hashKey) ? hashKey : null;
		otpTtl = otpTtl == null || otpTtl.isNegative() || otpTtl.isZero() ? Duration.ofMinutes(10) : otpTtl;
		otpTestNumberPrefix = StringUtils.hasText(otpTestNumberPrefix) ? normalisePrefix(otpTestNumberPrefix)
			: DEFAULT_TEST_PREFIX;
	}
	
	static String normalisePrefix(String input) {
		String prefix = SEPARATORS.matcher(input).replaceAll("");
		if (prefix.startsWith("91")) {
			prefix = "+" + prefix;
		} else if (!prefix.startsWith("+")) {
			prefix = "+91" + prefix;
		}
		if (!TEST_PREFIX.matcher(prefix).matches()) {
			throw new IllegalArgumentException("OTP_TEST_NUMBER_PREFIX must be +91 followed by 1 to 9 digits, got: "
				+ input);
		}
		return prefix;
	}
	
	public IdentityProperties(String hashKey, Duration otpTtl) {
		this(hashKey, otpTtl, null);
	}
	
	/** Whether this E.164 number is in the test range: its code is never sent anywhere. */
	public boolean isTestNumber(String phoneE164) {
		return phoneE164 != null && phoneE164.startsWith(otpTestNumberPrefix);
	}
	
}
