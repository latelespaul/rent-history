package com.rentrix.rentrixserver.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRevisionRepository extends JpaRepository<ReviewRevision, Long> {
	
	List<ReviewRevision> findByReviewIdOrderByRevisionNoAsc(Long reviewId);
	
}
