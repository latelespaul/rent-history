package com.rentrix.rentrixserver.controller;

import com.rentrix.rentrixserver.dto.ReviewDto;
import com.rentrix.rentrixserver.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
public class ReviewController {
	
	private final ReviewService reviewService;
	
	@Autowired
	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}
	
	@GetMapping
	public Page<ReviewDto> getAllReviews(@PageableDefault(sort = "reviewDate") Pageable pageable) {
		return reviewService.getAllReviews(pageable);
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<ReviewDto> getReviewById(@PathVariable Long id) {
		return ResponseEntity.ok(reviewService.getReviewById(id));
	}
	
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
		reviewService.deleteReview(id);
		return ResponseEntity.noContent().build();
	}
	
}