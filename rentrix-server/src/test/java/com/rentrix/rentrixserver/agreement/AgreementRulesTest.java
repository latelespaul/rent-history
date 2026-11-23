package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.storage.DocumentType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure rules of rental agreement intake: Aadhaar masking (D-131), anchor kinds (D-124), retention (D-131). */
class AgreementRulesTest {
	
	@Test
	void masksEveryAadhaarLikeNumberAndKeepsTheLastFour() {
		assertThat(AadhaarMask.mask("Aadhaar 2345 6789 0123")).isEqualTo("Aadhaar XXXX XXXX 0123");
		assertThat(AadhaarMask.mask("234567890123")).isEqualTo("XXXX XXXX 0123");
		assertThat(AadhaarMask.mask("2345-6789-0123 and 9876 5432 1098")).isEqualTo("XXXX XXXX 0123 and XXXX XXXX 1098");
		// Not 12 digits on their own: a 13-digit number, a phone number, a rent.
		assertThat(AadhaarMask.mask("1234567890123")).isEqualTo("1234567890123");
		assertThat(AadhaarMask.mask("+91 98765 43210")).isEqualTo("+91 98765 43210");
		assertThat(AadhaarMask.mask("Rent 25000")).isEqualTo("Rent 25000");
		assertThat(AadhaarMask.mask(null)).isNull();
		assertThat(AadhaarMask.contains("ID 2345 6789 0123")).isTrue();
		assertThat(AadhaarMask.contains("4567/2025")).isFalse();
	}
	
	@Test
	void theManualExtractorMasksEveryTypedTextAndFlagsIt() {
		AgreementFields typed = new AgreementFields(" Ravi  Patil ", "Asha 2345 6789 0123", "Baner, Pune",
			"411045", 25_000L, null, LocalDate.of(2026, 1, 1), null, " in-ka 234567890123 ", null);
		AgreementExtractor.ExtractedAgreement out = new ManualAgreementExtractor().extract(new byte[0], DocumentType.PDF,
			typed);
		assertThat(out.extractor()).isEqualTo("MANUAL");
		assertThat(out.fields().landlordName()).isEqualTo("Ravi Patil");
		assertThat(out.fields().tenantName()).isEqualTo("Asha XXXX XXXX 0123");
		assertThat(out.fields().anchorNumber()).isEqualTo("IN-KA XXXX XXXX 0123");
		assertThat(out.flags()).containsExactly(AgreementFlag.AADHAAR_MASKED_ON_INTAKE);
		
		AgreementExtractor.ExtractedAgreement clean = new ManualAgreementExtractor().extract(new byte[0],
			DocumentType.PDF, new AgreementFields("Ravi", "Asha", null, null, 1L, null, LocalDate.of(2026, 1, 1), null,
				"", null));
		assertThat(clean.flags()).isEmpty();
		assertThat(clean.fields().anchorNumber()).isNull();
	}
	
	@Test
	void theAnchorKindFollowsTheHomesState() {
		assertThat(AnchorKind.forState("Maharashtra")).isEqualTo(AnchorKind.MH_LEAVE_LICENCE);
		assertThat(AnchorKind.forState(" karnataka ")).isEqualTo(AnchorKind.KA_ESTAMP);
		assertThat(AnchorKind.forState("Telangana")).isEqualTo(AnchorKind.OTHER_STAMP);
	}
	
	@Test
	void theFileGoes30DaysAfterTheLaterOfDecisionAndConfirmationElse90DaysAfterUpload() {
		AgreementRetention rule = new AgreementRetention(30);
		Instant upload = Instant.parse("2026-01-01T00:00:00Z");
		Instant decided = upload.plus(Duration.ofDays(5));
		Instant confirmed = upload.plus(Duration.ofDays(10));
		
		assertThat(rule.purgeAfter(upload, null, null)).isEqualTo(upload.plus(Duration.ofDays(90)));
		assertThat(rule.purgeAfter(upload, null, confirmed)).isEqualTo(upload.plus(Duration.ofDays(90)));
		assertThat(rule.purgeAfter(upload, decided, null)).isEqualTo(decided.plus(Duration.ofDays(30)));
		assertThat(rule.purgeAfter(upload, decided, confirmed)).isEqualTo(confirmed.plus(Duration.ofDays(30)));
		assertThat(rule.purgeAfter(upload, confirmed.plus(Duration.ofDays(1)), confirmed))
			.isEqualTo(confirmed.plus(Duration.ofDays(31)));
	}
	
	@Test
	void aFileUploadedOnDayZeroAndDecidedOnDayTwoGoesOnDay32() {
		AgreementRetention rule = new AgreementRetention(30);
		Instant upload = Instant.parse("2026-01-01T00:00:00Z");
		
		assertThat(rule.purgeAfter(upload, upload.plus(Duration.ofDays(2)), null))
			.isEqualTo(upload.plus(Duration.ofDays(32)));
	}
	
}
