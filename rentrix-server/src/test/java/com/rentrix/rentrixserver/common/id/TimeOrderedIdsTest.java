package com.rentrix.rentrixserver.common.id;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class TimeOrderedIdsTest {
	
	@Test
	void idsArePositiveUniqueAndTimeOrdered() {
		TimeOrderedIds ids = new TimeOrderedIds(7);
		Set<Long> seen = new HashSet<>();
		long previousMillis = 0;
		for (int i = 0; i < 50_000; i++) {
			long id = ids.nextId();
			assertThat(id).isPositive();
			assertThat(seen.add(id)).isTrue();
			long millis = id >>> (TimeOrderedIds.NODE_BITS + TimeOrderedIds.SEQUENCE_BITS);
			assertThat(millis).isGreaterThanOrEqualTo(previousMillis);
			previousMillis = millis;
			assertThat((id >>> TimeOrderedIds.SEQUENCE_BITS) & TimeOrderedIds.MAX_NODE).isEqualTo(7);
		}
	}
	
	@Test
	void nodeComesFromEnvOrIsRandomInRange() {
		assertThat(TimeOrderedIds.nodeFromEnv("12")).isEqualTo(12);
		assertThat(TimeOrderedIds.nodeFromEnv(null)).isBetween(0L, TimeOrderedIds.MAX_NODE);
		assertThatThrownBy(() -> new TimeOrderedIds(1024)).isInstanceOf(IllegalArgumentException.class);
	}
	
	@Test
	void uuidV7HasVersionVariantAndTime() {
		long millis = 1_763_700_000_000L;
		UUID uuid = UuidV7.at(millis);
		assertThat(uuid.version()).isEqualTo(7);
		assertThat(uuid.variant()).isEqualTo(2);
		assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(millis);
		assertThat(UuidV7.next()).isNotEqualTo(UuidV7.next());
	}
	
}
