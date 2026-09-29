package com.rentrix.rentrixserver.service;

import com.rentrix.rentrixserver.dto.CreateReviewRequest;
import com.rentrix.rentrixserver.dto.ReviewDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
	
	Page<ReviewDto> getAllReviews(Pageable pageable);
	
	ReviewDto getReviewById(Long id);
	
	Page<ReviewDto> getReviewsByFlat(Long flatId, Pageable pageable);
	
	ReviewDto createReview(Long flatId, Long userId, CreateReviewRequest req);
	
	Page<ReviewDto> getMyReviews(Long userId, Pageable pageable);
	
	String deleteReview(Long id);
	
}