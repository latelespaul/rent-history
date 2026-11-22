package com.rentrix.rentrixserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Identity settings (§17, D-101, D-106). {@code hashKey} comes from {@code ID_HASH_KEY} (dev and test ship a
 * built-in one); {@code otpTtl} from {@code OTP_TTL_MINUTES}.
 */
@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(String hashKey, Duration otpTtl) {
	
	public IdentityProperties {
		hashKey = StringUtils.hasText(hashKey) ? hashKey : null;
		otpTtl = otpTtl == null || otpTtl.isNegative() || otpTtl.isZero() ? Duration.ofMinutes(10) : otpTtl;
	}
	
}
