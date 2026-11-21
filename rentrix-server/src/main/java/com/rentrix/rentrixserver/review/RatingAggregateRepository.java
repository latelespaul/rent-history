package com.rentrix.rentrixserver.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingAggregateRepository extends JpaRepository<RatingAggregate, RatingAggregateId> {
	
	/** "Top rated" listing (index ix_rating_aggregates_rank); pass the D-53 sort in the pageable. */
	Page<RatingAggregate> findByIdSubjectType(RatingSubjectType subjectType, Pageable pageable);
	
}
