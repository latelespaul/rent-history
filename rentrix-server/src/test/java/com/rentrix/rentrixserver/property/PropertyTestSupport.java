package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Helpers for property and stats tests: tokens, throw-away live cities and stored aggregates. */
@Component
@RequiredArgsConstructor
public class PropertyTestSupport {
	
	private final AccessTokenService tokens;
	private final JdbcTemplate jdbc;
	private final RatingAggregateRepository aggregates;
	
	public String bearer(User user) {
		return "Bearer " + tokens.issue(user.getPublicId(), user.getRoles());
	}
	
	/** An admin console session (§13.3); send it with {@code AdminConsole.adminHost()}. */
	public String adminBearer(User user) {
		return "Bearer " + tokens.issueAdmin(user.getPublicId());
	}
	
	/** A city row only this test sees (call inside a rolled-back test transaction). */
	public void city(String code, String name, boolean live) {
		jdbc.update("insert into cities (code, name, aliases, state, live, pincode_prefixes, created_at, updated_at) "
			+ "values (?, ?, null, 'Test State', ?, null, current_timestamp, current_timestamp)", code, name, live);
	}
	
	/** Stores a PROPERTY aggregate with the given count and average; bayes per D-53 with m = 3.5. */
	public void rating(Property property, int count, double average) {
		RatingAggregate aggregate = new RatingAggregate(RatingSubjectType.PROPERTY, property.getId());
		int sum = (int) Math.round(count * average);
		aggregate.setReviewCount(count);
		aggregate.setRatingSum(sum);
		aggregate.setAverage(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
		aggregate.setBayesScore(BigDecimal.valueOf((5 * 3.5 + sum) / (5 + count)).setScale(3, RoundingMode.HALF_UP));
		aggregate.setLastReviewAt(Instant.parse("2026-01-01T00:00:00Z"));
		aggregates.saveAndFlush(aggregate);
	}
	
}
