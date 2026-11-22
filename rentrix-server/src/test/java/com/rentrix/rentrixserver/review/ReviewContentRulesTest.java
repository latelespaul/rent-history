package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-43 unit-number rule: only explicit unit markers or block-unit codes are personal data. */
class ReviewContentRulesTest {

	private static final String PAD = " The landlord fixed every repair quickly and fairly.";

	@ParameterizedTest
	@ValueSource(strings = {
		"We lived in this house 3 years",
		"We moved into the flat 2 years ago",
		"The second bedroom, room 2 was small",
		"He is the #1 landlord in the area",
		"The flat is 10 min from the metro and 2 km from the market",
		"Close to NH-48 and the IT park, easy commute",
		"The house is right on SH-17 near the bus stand",
		"A 1200 sq ft flat with good light",
		"A 1500 rent hike every year",
	})
	void honestSentencesPass(String text) {
		assertThat(ReviewContentRules.review(text + PAD, null)).isEqualTo(text + PAD);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"We lived in flat no 1203",
		"Our house no. 12 had a garden",
		"Unit #5 faces the road",
		"We stayed in A-1203",
		"We stayed in B 402",
		"We lived in flat B 402",
		"Tower C-1204 has a slow lift",
	})
	void unitNumbersAreRejected(String text) {
		assertThatThrownBy(() -> ReviewContentRules.review(text + PAD, null))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrors())
				.extracting(ApiProblem.FieldError::code).contains("contains_unit_number"));
	}

}
