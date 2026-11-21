package com.rentrix.rentrixserver.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewResponseRepository extends JpaRepository<ReviewResponse, Long> {
	
	Optional<ReviewResponse> findByPublicId(UUID publicId);
	
	Optional<ReviewResponse> findByReviewId(Long reviewId);
	
}
