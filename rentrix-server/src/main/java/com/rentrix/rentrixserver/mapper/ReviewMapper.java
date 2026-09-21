package com.rentrix.rentrixserver.mapper;

import com.rentrix.rentrixserver.dto.ReviewDto;
import com.rentrix.rentrixserver.entity.Flat;
import com.rentrix.rentrixserver.entity.Review;
import com.rentrix.rentrixserver.entity.User;
import com.rentrix.rentrixserver.repository.FlatRepository;
import com.rentrix.rentrixserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ReviewMapper {
	private final UserRepository userRepository;
	private final FlatRepository flatRepository;
	
	public ReviewDto toDto(Review review) {
		ReviewDto dto = new ReviewDto();
		
		dto.setId(review.getId());
		dto.setUserId(review.getUser().getId());
		dto.setUserName(review.getUser().getName());
		dto.setFlatId(review.getFlat().getId());
		dto.setTitle(review.getTitle());
		dto.setContent(review.getContent());
		dto.setRating(review.getRating());
		dto.setReviewDate(review.getReviewDate());
		
		return dto;
	}
	
	public Review toEntity(ReviewDto dto) {
		Review review = new Review();
		User user = userRepository.findById(dto.getUserId()).orElseThrow();
		Flat flat = flatRepository.findById(dto.getFlatId()).orElseThrow();
		review.setId(dto.getId());
		review.setUser(user);
		review.setFlat(flat);
		review.setTitle(dto.getTitle());
		review.setContent(dto.getContent());
		review.setRating(dto.getRating());
		review.setReviewDate(dto.getReviewDate());
		
		return review;
	}
	
}