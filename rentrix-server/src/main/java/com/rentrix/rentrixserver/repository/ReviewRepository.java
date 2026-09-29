package com.rentrix.rentrixserver.repository;

import com.rentrix.rentrixserver.entity.Review;
import com.rentrix.rentrixserver.entity.constants.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
	
	Page<Review> findByFlatIdAndStatus(Long flatId, ReviewStatus status, Pageable pageable);
	
	Page<Review> findByUserId(Long userId, Pageable pageable);
	
	Page<Review> findByStatus(ReviewStatus status, Pageable pageable);
	
}