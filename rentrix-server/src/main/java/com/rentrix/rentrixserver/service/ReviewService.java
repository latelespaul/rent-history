package com.rentrix.rentrixserver.service;

import com.rentrix.rentrixserver.dto.ReviewDto;

import java.util.List;

public interface ReviewService {
	
	List<ReviewDto> getAllReviews();
	
	ReviewDto getReviewById(Long id);
	
	ReviewDto createReview(ReviewDto reviewDto);
	
	String deleteReview(Long id);
	
}