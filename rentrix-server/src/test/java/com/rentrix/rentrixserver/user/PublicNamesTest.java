package com.rentrix.rentrixserver.user;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The one public short-name rule (D-55): first word + initial of the last name word, skipping code words. */
class PublicNamesTest {
	
	@Test
	void firstWordAndLastNameInitial() {
		assertThat(PublicNames.shortName("Asha Rao")).isEqualTo("Asha R.");
		assertThat(PublicNames.shortName("  asha   kumari rao ")).isEqualTo("asha R.");
		assertThat(PublicNames.shortName("Mary-Jane O'Brien")).isEqualTo("Mary-Jane O.");
		assertThat(PublicNames.shortName("Ananya Rao-Singh")).isEqualTo("Ananya R.");
		assertThat(PublicNames.shortName("Élodie émile")).isEqualTo("Élodie É.");
	}
	
	@Test
	void skipsCodeWordsLikeSeedCodes() {
		assertThat(PublicNames.shortName("Demo Tenant BLR-1")).isEqualTo("Demo T.");
		assertThat(PublicNames.shortName("Demo Landlord PNQ-2")).isEqualTo("Demo L.");
		assertThat(PublicNames.shortName("Ravi Kumar 2")).isEqualTo("Ravi K.");
		assertThat(PublicNames.shortName("Fresh User 1a2b3c4d")).isEqualTo("Fresh U.");
	}
	
	@Test
	void oneWordOrNoNameWordAfterTheFirst() {
		assertThat(PublicNames.shortName("Cher")).isEqualTo("Cher");
		assertThat(PublicNames.shortName("Cher 42")).isEqualTo("Cher");
		assertThat(PublicNames.shortName("  ")).isEqualTo("Member");
		assertThat(PublicNames.shortName(null)).isEqualTo("Member");
	}
	
}
