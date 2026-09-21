package com.rentrix.rentrixserver.service.impl;

import com.rentrix.rentrixserver.dto.ReviewDto;
import com.rentrix.rentrixserver.entity.Review;
import com.rentrix.rentrixserver.mapper.ReviewMapper;
import com.rentrix.rentrixserver.repository.ReviewRepository;
import com.rentrix.rentrixserver.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ReviewServiceImpl implements ReviewService {
	private final ReviewMapper reviewMapper;
	private final ReviewRepository reviewRepository;
	
	public List<ReviewDto> getAllReviews() {
		List<Review> reviews = reviewRepository.findAll();
		List<ReviewDto> reviewDtos = new ArrayList<>();
		reviews.forEach(review -> reviewDtos.add(reviewMapper.toDto(review)));
		return reviewDtos;
	}
	
	public ReviewDto getReviewById(Long id) {
		Review review = reviewRepository.findById(id).orElseThrow();
		return reviewMapper.toDto(review);
	}
	
	public ReviewDto createReview(ReviewDto reviewDto) {
		Review review = reviewMapper.toEntity(reviewDto);
		reviewRepository.save(review);
		return reviewMapper.toDto(review);
	}
	
	public String deleteReview(Long id) {
		reviewRepository.findById(id).orElseThrow();
		reviewRepository.deleteById(id);
		return "Review with id: " + id + " has been deleted";
	}
	
}