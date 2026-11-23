package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.notification.sms.PhoneMasks;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityUnitTest {
	
	@Test
	void linkedinUrlsAreNormalised() {
		assertThat(LinkedinUrls.normalise("https://www.linkedin.com/in/asha-rao")).contains("https://www.linkedin.com/in/asha-rao");
		assertThat(LinkedinUrls.normalise("linkedin.com/in/Asha_Rao/")).contains("https://www.linkedin.com/in/asha_rao");
		assertThat(LinkedinUrls.normalise(" http://in.linkedin.com/in/asha?trk=x#top ")).contains("https://www.linkedin.com/in/asha");
		assertThat(LinkedinUrls.normalise("https://www.linkedin.com/company/rentrix")).isEmpty();
		assertThat(LinkedinUrls.normalise("https://evil.com/linkedin.com/in/asha")).isEmpty();
		assertThat(LinkedinUrls.normalise("https://www.linkedin.com.evil.com/in/asha")).isEmpty();
		assertThat(LinkedinUrls.normalise("https://www.linkedin.com/in/ab")).isEmpty();
		assertThat(LinkedinUrls.normalise(null)).isEmpty();
	}
	
	@Test
	void indianMobileNumbersAreNormalisedToE164() {
		assertThat(PhoneOtpService.normalisePhone("+91 98765 43210")).contains("+919876543210");
		assertThat(PhoneOtpService.normalisePhone("98765-43210")).contains("+919876543210");
		assertThat(PhoneOtpService.normalisePhone("919876543210")).contains("+919876543210");
		assertThat(PhoneOtpService.normalisePhone("+915876543210")).isEmpty();
		assertThat(PhoneOtpService.normalisePhone("+14155550123")).isEmpty();
		assertThat(PhoneOtpService.normalisePhone("")).isEmpty();
	}
	
	@Test
	void theTestNumberPrefixIsNormalisedLikeAPhoneNumber() {
		for (String written : new String[] {"+91999990", "+91 99999 0", "91999990", "999990", " (99999)-0 ", "+91.99999.0"}) {
			IdentityProperties properties = new IdentityProperties(null, null, written);
			assertThat(properties.otpTestNumberPrefix()).as(written).isEqualTo("+91999990");
			assertThat(properties.isTestNumber("+919999904321")).as(written).isTrue();
			assertThat(properties.isTestNumber("+919999914321")).as(written).isFalse();
		}
		assertThat(new IdentityProperties(null, null, null).otpTestNumberPrefix()).isEqualTo("+91999990");
		assertThat(new IdentityProperties(null, null, "  ").otpTestNumberPrefix()).isEqualTo("+91999990");
	}
	
	@Test
	void aMalformedTestNumberPrefixFailsAtStartup() {
		for (String bad : new String[] {"+1415", "+91", "+91abc", "+919999900000", "+91 9999x", "++91999990"}) {
			assertThatThrownBy(() -> new IdentityProperties(null, null, bad)).as(bad)
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OTP_TEST_NUMBER_PREFIX");
		}
	}
	
	@Test
	void masksShowTheLastFourOnly() {
		assertThat(PhoneMasks.mask("+919876543210")).isEqualTo("+91 XXXXXX3210");
		assertThat(PhoneMasks.mask("+14155550123")).isEqualTo("XXXXXX0123");
		assertThat(PhoneMasks.mask(null)).isNull();
		IdentityVerification pan = new IdentityVerification();
		pan.setMethod(IdentityMethod.PAN);
		pan.setLast4("234F");
		assertThat(IdentityService.masked(pan)).isEqualTo("XXXXXX234F");
		IdentityVerification aadhaar = new IdentityVerification();
		aadhaar.setMethod(IdentityMethod.AADHAAR);
		aadhaar.setLast4("1234");
		assertThat(IdentityService.masked(aadhaar)).isEqualTo("XXXX XXXX 1234");
	}
	
	@Test
	void hmacNeedsAKeyAndIsStable() {
		IdHasher hasher = new IdHasher(new IdentityProperties("k".repeat(32), null));
		assertThat(hasher.available()).isTrue();
		assertThat(hasher.hmac("+919876543210")).hasSize(64).isEqualTo(hasher.hmac("+919876543210"))
			.isNotEqualTo(new IdHasher(new IdentityProperties("j".repeat(32), null)).hmac("+919876543210"));
		assertThat(new IdHasher(new IdentityProperties("", null)).available()).isFalse();
		assertThat(IdHasher.same("ab", "ab")).isTrue();
		assertThat(IdHasher.same("ab", null)).isFalse();
	}
	
}
