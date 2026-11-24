package com.rentrix.rentrixserver.location;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The typo-tolerant word-prefix match and the flat-number split of the search box (D-164). */
class NameMatchTest {

	private static int cost(String typed, String name) {
		return NameMatch.cost(NameMatch.words(typed), NameMatch.words(name));
	}

	@Test
	void everyTypedWordStartsAWordOfTheNameInAnyOrder() {
		assertThat(cost("prestige lake", "Prestige Lakeside")).isZero();
		assertThat(cost("LAKESIDE", "Prestige Lakeside")).isZero();
		assertThat(cost("lakeside prestige", "Prestige Lakeside")).isZero();
		assertThat(cost("prestige x", "Prestige Lakeside")).isEqualTo(NameMatch.NONE);
		assertThat(cost("prestige prestige", "Prestige Lakeside")).isEqualTo(NameMatch.NONE);
		assertThat(cost("", "Prestige Lakeside")).isEqualTo(NameMatch.NONE);
	}

	@Test
	void allowsATypoFromFourLettersAndTwoFromSeven() {
		assertThat(cost("prestge", "Prestige Lakeside")).isEqualTo(1);
		assertThat(cost("presitge lakesdie", "Prestige Lakeside")).isEqualTo(2);
		assertThat(cost("lkae", "Prestige Lakeside")).isEqualTo(1);
		assertThat(cost("lak", "Prestige Lakeside")).isZero();
		assertThat(cost("lxk", "Prestige Lakeside")).isEqualTo(NameMatch.NONE);
		assertThat(cost("prxxtige", "Prestige Lakeside")).isEqualTo(2);
		assertThat(cost("pxxxtige", "Prestige Lakeside")).isEqualTo(NameMatch.NONE);
	}

	@Test
	void numbersMatchExactly() {
		assertThat(cost("sector 21", "Sector 21 Heights")).isZero();
		assertThat(cost("sector 22", "Sector 21 Heights")).isEqualTo(NameMatch.NONE);
	}

	@Test
	void splitsATrailingFlatNumberFromThePlaceWords() {
		assertThat(PlaceMatcher.Query.parse("Prestige Lakeside 1204"))
			.isEqualTo(new PlaceMatcher.Query(List.of("prestige", "lakeside"), "1204"));
		assertThat(PlaceMatcher.Query.parse("  Lakeside   B-1204 "))
			.isEqualTo(new PlaceMatcher.Query(List.of("lakeside"), "B-1204"));
		assertThat(PlaceMatcher.Query.parse("Lakeside flat no 1204"))
			.isEqualTo(new PlaceMatcher.Query(List.of("lakeside"), "1204"));
		// a PIN code, a lone number and a name without one are no flat
		assertThat(PlaceMatcher.Query.parse("HSR Layout 560102").unit()).isNull();
		assertThat(PlaceMatcher.Query.parse("1204").unit()).isNull();
		assertThat(PlaceMatcher.Query.parse("Prestige Lakeside").unit()).isNull();
		assertThat(PlaceMatcher.Query.parse("flat 1204").unit()).isNull();
	}

}
