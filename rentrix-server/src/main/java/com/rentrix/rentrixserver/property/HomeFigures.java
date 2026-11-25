package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.RatingAggregateId;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.review.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The per-home figures every home card and society group reads (D-165, D-166): each home's rating aggregate and its
 * count of listed reviews, so a society's figures always add up the same numbers its home cards show.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeFigures {

	private final RatingAggregateRepository aggregates;
	private final ReviewRepository reviews;

	/** {@code [property id → rating aggregate]}; a home without reviews has no entry. */
	public Map<Long, RatingAggregate> ratings(Collection<Long> homeIds) {
		if (homeIds.isEmpty()) {
			return Map.of();
		}
		List<RatingAggregateId> keys = homeIds.stream()
			.map(id -> new RatingAggregateId(RatingSubjectType.PROPERTY, id)).toList();
		return aggregates.findAllById(keys).stream()
			.collect(Collectors.toMap(a -> a.getId().subjectId(), Function.identity()));
	}

	/** {@code [property id → published home reviews]}; a home without any has no entry. */
	public Map<Long, Long> listedReviews(Collection<Long> homeIds) {
		Map<Long, Long> counts = new HashMap<>();
		if (homeIds.isEmpty()) {
			return counts;
		}
		for (Object[] row : reviews.countListedForProperties(homeIds)) {
			counts.put((Long) row[0], ((Number) row[1]).longValue());
		}
		return counts;
	}

}
