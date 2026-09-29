package com.rentrix.rentrixserver.service;

import com.rentrix.rentrixserver.dto.ReviewDto;
import com.rentrix.rentrixserver.entity.constants.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminService {
	
	Page<ReviewDto> getReviewsByStatus(ReviewStatus status, Pageable pageable);
	
	ReviewDto moderate(Long reviewId, ReviewStatus status);
	
}