package com.rentrix.rentrixserver.service.impl;

import com.rentrix.rentrixserver.dto.ReviewDto;
import com.rentrix.rentrixserver.entity.Review;
import com.rentrix.rentrixserver.entity.constants.ReviewStatus;
import com.rentrix.rentrixserver.exception.ApiException;
import com.rentrix.rentrixserver.mapper.ReviewMapper;
import com.rentrix.rentrixserver.repository.ReviewRepository;
import com.rentrix.rentrixserver.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
	
	private final ReviewRepository reviewRepository;
	
	@Override
	public Page<ReviewDto> getReviewsByStatus(ReviewStatus status, Pageable pageable) {
		return reviewRepository.findByStatus(status, pageable).map(ReviewMapper::toDto);
	}
	
	@Override
	@Transactional
	public ReviewDto moderate(Long reviewId, ReviewStatus status) {
		if (status == ReviewStatus.PENDING) {
			throw ApiException.badRequest("Cannot set status back to PENDING");
		}
		
		Review review = reviewRepository.findById(reviewId)
												  .orElseThrow(() -> ApiException.notFound("Review not found"));
		
		review.setStatus(status);
		Review saved = reviewRepository.save(review);
		
		log.info("Moderated review {} → {}", reviewId, status);
		return ReviewMapper.toDto(saved);
	}
	
}