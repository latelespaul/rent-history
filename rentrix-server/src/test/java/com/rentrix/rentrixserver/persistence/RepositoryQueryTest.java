package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

/** The indexed lookups later endpoints build on: search by city and rating ranking. */
@IntegrationTest
@Transactional
class RepositoryQueryTest {
	
	@Autowired
	TestData data;
	
	@Autowired
	PropertyRepository properties;
	
	@Autowired
	RatingAggregateRepository aggregates;
	
	@Test
	void searchByCityAndLocalityPrefixListsPublishedOnly() {
		User landlord = data.user("Demo Landlord", Role.LANDLORD);
		String locality = "Search Town " + System.nanoTime();
		Property a = data.property(landlord, "BLR", locality + " East", PropertyStatus.PUBLISHED);
		Property b = data.property(landlord, "BLR", locality + " West", PropertyStatus.PUBLISHED);
		data.property(landlord, "BLR", locality + " Draft", PropertyStatus.DRAFT);
		data.property(landlord, "PNQ", locality + " Pune", PropertyStatus.PUBLISHED);
		
		Page<Property> page = properties.findByCityCodeAndStatusAndLocalityKeyStartingWithAndDeletedAtIsNull("BLR",
			PropertyStatus.PUBLISHED, Property.localityKey(locality), PageRequest.of(0, 1, Sort.by("localityKey")));
		
		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getTotalPages()).isEqualTo(2);
		assertThat(page.getContent()).extracting(Property::getId).containsExactly(a.getId());
		assertThat(properties.findByCityCodeAndStatusAndDeletedAtIsNull("BLR", PropertyStatus.PUBLISHED,
			PageRequest.of(0, 50)).getContent()).extracting(Property::getId).contains(a.getId(), b.getId());
	}
	
	@Test
	void topRatedSortsByBayesScoreThenCount() {
		User landlord = data.user("Demo Landlord", Role.LANDLORD);
		aggregates.saveAndFlush(aggregate(data.property(landlord, "PNQ", "Baner", PropertyStatus.PUBLISHED), "3.900", 9));
		aggregates.saveAndFlush(aggregate(data.property(landlord, "PNQ", "Baner", PropertyStatus.PUBLISHED), "4.100", 3));
		aggregates.saveAndFlush(aggregate(data.property(landlord, "PNQ", "Baner", PropertyStatus.PUBLISHED), "3.900", 12));
		
		List<RatingAggregate> top = aggregates.findByIdSubjectType(RatingSubjectType.PROPERTY,
			PageRequest.of(0, 50, Sort.by(Sort.Order.desc("bayesScore"), Sort.Order.desc("reviewCount")))).getContent();
		
		assertThat(top).extracting(RatingAggregate::getBayesScore).isSortedAccordingTo((x, y) -> y.compareTo(x));
		List<Integer> counts = top.stream().filter(r -> r.getBayesScore().compareTo(new BigDecimal("3.9")) == 0)
			.map(RatingAggregate::getReviewCount).toList();
		assertThat(counts).isSortedAccordingTo((x, y) -> y - x);
	}
	
	private static RatingAggregate aggregate(Property property, String bayes, int count) {
		RatingAggregate aggregate = new RatingAggregate(RatingSubjectType.PROPERTY, property.getId());
		aggregate.setBayesScore(new BigDecimal(bayes));
		aggregate.setReviewCount(count);
		return aggregate;
	}
	
}
