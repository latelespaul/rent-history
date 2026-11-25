package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.dto.AreaRef;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.property.dto.RatingSummary;
import com.rentrix.rentrixserver.property.dto.SocietyGroup;
import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.RatingAggregateId;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.tenancy.RentRecord;
import com.rentrix.rentrixserver.tenancy.RentRecordRepository;
import com.rentrix.rentrixserver.tenancy.RentRecordStatus;
import com.rentrix.rentrixserver.tenancy.TenancyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@link SocietyGroup} of a society from the homes it stands for (D-166): the search card of a society
 * with 2+ matching homes and the society page summary. Every figure is computed at read time over those homes, like
 * the tower figures of D-111, so no stored roll-up has to follow them (D-33).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocietyGroups {

	private final RatingAggregateRepository aggregates;
	private final ReviewRepository reviews;
	private final RentRecordRepository rentRecords;

	/**
	 * One group per society in {@code homes} (society id → its homes' internal ids). {@code withRent} adds the rent
	 * range: signed-in callers only (D-56).
	 */
	public Map<Long, SocietyGroup> of(Map<Society, List<Long>> homes, boolean withRent) {
		List<Long> all = homes.values().stream().flatMap(List::stream).toList();
		Map<Long, RatingAggregate> ratings = ratings(all);
		Map<Long, Long> listed = listed(all);
		Map<Long, Long> rents = withRent ? latestOpenRents(all) : Map.of();
		Map<Long, SocietyGroup> groups = new HashMap<>();
		homes.forEach((society, ids) -> {
			int counted = 0;
			long sum = 0;
			long listedReviews = 0;
			long min = Long.MAX_VALUE;
			long max = Long.MIN_VALUE;
			for (Long id : ids) {
				RatingAggregate rating = ratings.get(id);
				if (rating != null) {
					counted += rating.getReviewCount();
					sum += rating.getRatingSum();
				}
				listedReviews += listed.getOrDefault(id, 0L);
				Long rent = rents.get(id);
				if (rent != null) {
					min = Math.min(min, rent);
					max = Math.max(max, rent);
				}
			}
			BigDecimal average = counted >= PropertySearchQuery.MIN_REVIEWS_FOR_AVERAGE
				? BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(counted), 1, RoundingMode.HALF_UP) : null;
			groups.put(society.getId(), new SocietyGroup(society.getPublicId(), society.getName(),
				AreaRef.of(society.getArea()), CityRef.of(society.getCity()),
				society.getStatus() == LocationStatus.PENDING, ids.size(), (int) listedReviews,
				new RatingSummary(counted, average),
				min == Long.MAX_VALUE ? null : new SocietyGroup.RentRange(min, max)));
		});
		return groups;
	}

	private Map<Long, RatingAggregate> ratings(Collection<Long> homeIds) {
		Map<Long, RatingAggregate> byHome = new HashMap<>();
		if (homeIds.isEmpty()) {
			return byHome;
		}
		List<RatingAggregateId> keys = homeIds.stream()
			.map(id -> new RatingAggregateId(RatingSubjectType.PROPERTY, id)).toList();
		aggregates.findAllById(keys).forEach(a -> byHome.put(a.getId().subjectId(), a));
		return byHome;
	}

	private Map<Long, Long> listed(Collection<Long> homeIds) {
		Map<Long, Long> counts = new HashMap<>();
		if (homeIds.isEmpty()) {
			return counts;
		}
		for (Object[] row : reviews.countListedForProperties(homeIds)) {
			counts.put((Long) row[0], ((Number) row[1]).longValue());
		}
		return counts;
	}

	/**
	 * Each home's latest rent as its rent history shows it (D-26, D-139): confirmed records of confirmed tenancies,
	 * and a one-sided tenancy's own records; only tenancies whose rent is open to everyone (D-140).
	 */
	private Map<Long, Long> latestOpenRents(Collection<Long> homeIds) {
		Map<Long, RentRecord> latest = new HashMap<>();
		if (homeIds.isEmpty()) {
			return Map.of();
		}
		for (RentRecord record : rentRecords.findByTenancyPropertyIdInAndStatusInAndTenancyStatusIn(homeIds,
			List.of(RentRecordStatus.CONFIRMED, RentRecordStatus.PROPOSED), TenancyService.CONFIRMED)) {
			boolean shown = record.getStatus() == RentRecordStatus.CONFIRMED
				|| record.getTenancy().getConfirmedAt() == null;
			if (!shown || record.getTenancy().getRentVisibility() != FieldVisibility.OPEN) {
				continue;
			}
			Long home = record.getTenancy().getProperty().getId();
			RentRecord seen = latest.get(home);
			if (seen == null || record.getEffectiveFrom().isAfter(seen.getEffectiveFrom())) {
				latest.put(home, record);
			}
		}
		Map<Long, Long> rents = new HashMap<>();
		latest.forEach((home, record) -> rents.put(home, record.getMonthlyRentInr()));
		return rents;
	}

}
