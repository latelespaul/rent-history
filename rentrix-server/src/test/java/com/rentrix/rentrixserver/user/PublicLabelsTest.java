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
	void longStayIsAYearOrMoreBeforeTheEndOrTheSubmitDay() {
		LocalDate start = LocalDate.of(2024, 3, 1);
		assertThat(PublicLabels.longStay(start, null, LocalDate.of(2025, 3, 1))).isTrue();
		assertThat(PublicLabels.longStay(start, null, LocalDate.of(2025, 2, 28))).isFalse();
		// An ended stay counts to its end, even when the review came much later.
		assertThat(PublicLabels.longStay(start, LocalDate.of(2024, 9, 30), LocalDate.of(2026, 1, 1))).isFalse();
		assertThat(PublicLabels.longStay(start, LocalDate.of(2025, 6, 30), LocalDate.of(2026, 1, 1))).isTrue();
		assertThat(PublicLabels.longStay(null, null, LocalDate.of(2026, 1, 1))).isFalse();
	}

}
