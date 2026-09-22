package com.rentrix.rentrixserver.service;

import com.rentrix.rentrixserver.dto.PageResponse;
import com.rentrix.rentrixserver.dto.ReviewDto;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
	
	PageResponse<ReviewDto> getAllReviews(Pageable pageable);
	
	ReviewDto getReviewById(Long id);
	
	String deleteReview(Long id);
	
}