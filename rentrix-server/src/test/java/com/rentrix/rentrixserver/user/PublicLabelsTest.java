package com.rentrix.rentrixserver.user;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The anonymous labels other people see instead of a name (D-168). */
class PublicLabelsTest {
	
	@Test
	void tenantShowsTheStartYearOfTheStay() {
		assertThat(PublicLabels.tenant(LocalDate.of(2024, 3, 1))).isEqualTo("Tenant, 2024");
		assertThat(PublicLabels.tenant(LocalDate.of(2019, 12, 31))).isEqualTo("Tenant, 2019");
		assertThat(PublicLabels.tenant(null)).isEqualTo("Tenant");
	}
	
	@Test
	void landlordIsOwnerWithNoInitials() {
		assertThat(PublicLabels.OWNER).isEqualTo("Owner");
	}
	
}
