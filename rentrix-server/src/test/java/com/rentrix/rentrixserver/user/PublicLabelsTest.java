package com.rentrix.rentrixserver.user;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The anonymous labels other people see instead of a name (D-168), with no year or date (D-169). */
class PublicLabelsTest {

	@Test
	void tenantLabelCarriesNoYear() {
		assertThat(PublicLabels.tenant(true)).isEqualTo("Verified tenant");
		assertThat(PublicLabels.tenant(false)).isEqualTo("Tenant");
	}

	@Test
	void landlordIsOwnerWithNoInitials() {
		assertThat(PublicLabels.OWNER).isEqualTo("Owner");
	}

	@Test
	void stayLengthIsABucketUpToTheEndOrTheSubmitDay() {
		LocalDate start = LocalDate.of(2024, 3, 1);
		assertThat(PublicLabels.stayLength(start, null, LocalDate.of(2025, 2, 28))).isEqualTo(StayLength.UNDER_ONE_YEAR);
		assertThat(PublicLabels.stayLength(start, null, LocalDate.of(2025, 3, 1))).isEqualTo(StayLength.ONE_TO_TWO_YEARS);
		assertThat(PublicLabels.stayLength(start, null, LocalDate.of(2026, 2, 28)))
			.isEqualTo(StayLength.ONE_TO_TWO_YEARS);
		assertThat(PublicLabels.stayLength(start, null, LocalDate.of(2026, 3, 1))).isEqualTo(StayLength.TWO_PLUS_YEARS);
		// An ended stay counts to its end, even when the review came much later.
		assertThat(PublicLabels.stayLength(start, LocalDate.of(2024, 9, 30), LocalDate.of(2027, 1, 1)))
			.isEqualTo(StayLength.UNDER_ONE_YEAR);
		assertThat(PublicLabels.stayLength(start, LocalDate.of(2025, 6, 30), LocalDate.of(2027, 1, 1)))
			.isEqualTo(StayLength.ONE_TO_TWO_YEARS);
		// No start date: no bucket at all.
		assertThat(PublicLabels.stayLength(null, null, LocalDate.of(2026, 1, 1))).isNull();
	}

}
