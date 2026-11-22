package com.rentrix.rentrixserver.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Truecaller phone verification for mobile websites (§17), from env only, one Truecaller web app per deployment:
 * {@code TRUECALLER_APP_KEY} (the app key, sent as {@code partnerKey} in the deep link) and
 * {@code TRUECALLER_APP_NAME} (the {@code partnerName} shown in the Truecaller sheet). Both are kept out of git and
 * out of the log. Without a key the "Verify with Truecaller" button is hidden. {@code TRUECALLER_ENABLED=false}
 * switches it off with a key set; {@code TRUECALLER_FAKE=true} (dev and test only) uses
 * {@link FakeTruecallerVerifier} instead of Truecaller.
 *
 * @param requestTtl how long a request nonce can still receive its callback (default 10 minutes, the life of a
 *                   Truecaller access token)
 * @param timeout    connect and read timeout of the profile fetch (default 5 s)
 */
@ConfigurationProperties(prefix = "app.truecaller")
public record TruecallerProperties(boolean enabled, String appKey, String appName, boolean fake,
	Duration requestTtl, Duration timeout) {
	
	public TruecallerProperties {
		appKey = StringUtils.hasText(appKey) ? appKey.trim() : null;
		appName = StringUtils.hasText(appName) ? appName.trim() : "Doorway Living";
		requestTtl = requestTtl == null || requestTtl.isNegative() || requestTtl.isZero() ? Duration.ofMinutes(10)
			: requestTtl;
		timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(5) : timeout;
	}
	
	/** Switched on and keyed; the phone hash key is checked by the caller. */
	public boolean configured() {
		return enabled && appKey != null;
	}
	
	@Override
	public String toString() {
		return "TruecallerProperties[enabled=" + enabled + ", appKey=" + (appKey == null ? null : "***")
			+ ", appName=***, fake=" + fake + ", requestTtl=" + requestTtl + ", timeout=" + timeout + "]";
	}
	
}
