package com.rentrix.rentrixserver.service.impl;

import com.rentrix.rentrixserver.dto.ReviewDto;
import com.rentrix.rentrixserver.entity.Review;
import com.rentrix.rentrixserver.exception.ApiException;
import com.rentrix.rentrixserver.mapper.ReviewMapper;
import com.rentrix.rentrixserver.repository.ReviewRepository;
import com.rentrix.rentrixserver.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
	
	private final ReviewRepository reviewRepository;
	
	@Override
	public Page<ReviewDto> getAllReviews(Pageable pageable) {
		return reviewRepository.findAll(pageable).map(ReviewMapper::toDto);
	}
	
	@Override
	public ReviewDto getReviewById(Long id) {
		Review review = reviewRepository.findById(id)
												  .orElseThrow(() -> ApiException.notFound("Review not found with id: " + id));
		return ReviewMapper.toDto(review);
	}
	
	@Override
	public String deleteReview(Long id) {
		if (!reviewRepository.existsById(id)) {
			throw ApiException.notFound("Review not found with id: " + id);
		}
		reviewRepository.deleteById(id);
		return "Review with id: " + id + " has been deleted";
	}
	
}