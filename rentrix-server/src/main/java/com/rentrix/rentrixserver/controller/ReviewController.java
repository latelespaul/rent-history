package com.rentrix.rentrixserver.controller;

import com.rentrix.rentrixserver.dto.ReviewDto;
import com.rentrix.rentrixserver.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
	
	private final ReviewService reviewService;
	
	@Autowired
	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}
	
	@GetMapping
	public List<ReviewDto> getAllReviews() {
		return reviewService.getAllReviews();
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<ReviewDto> getReviewById(@PathVariable Long id) {
		ReviewDto reviewDto = reviewService.getReviewById(id);
		return reviewDto != null ? ResponseEntity.ok(reviewDto) : ResponseEntity.notFound().build();
	}
	
	@PostMapping
	public ResponseEntity<ReviewDto> createReview(@RequestBody ReviewDto reviewDto) {
		ReviewDto savedReview = reviewService.createReview(reviewDto);
		return ResponseEntity.status(201).body(savedReview);
	}
	
	@PutMapping("/{id}")
	public ResponseEntity<ReviewDto> updateReview(@PathVariable Long id, @RequestBody ReviewDto reviewDto) {
		ReviewDto existingReview = reviewService.getReviewById(id);
		if (existingReview != null) {
			reviewDto.setId(id);
			ReviewDto updatedReview = reviewService.createReview(reviewDto);
			return ResponseEntity.ok(updatedReview);
		} else {
			return ResponseEntity.notFound().build();
		}
	}
	
	@DeleteMapping("/{id}")
	public ResponseEntity<String> deleteReview(@PathVariable Long id) {
		reviewService.deleteReview(id);
		return ResponseEntity.noContent().build();
	}
	
}