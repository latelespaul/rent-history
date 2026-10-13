package com.rentrix.rentrixserver.mapper;

import com.rentrix.rentrixserver.dto.response.ReviewDto;
import com.rentrix.rentrixserver.dto.response.ReviewProofDto;
import com.rentrix.rentrixserver.entity.Review;
import com.rentrix.rentrixserver.entity.ReviewProof;
import com.rentrix.rentrixserver.entity.constants.ReviewStatus;

import java.util.List;

public final class ReviewMapper {
	
	private ReviewMapper() {}
	
	public static ReviewDto toDto(Review review) {
		if (review == null) return null;
		
		List<ReviewProof> activeProofs = review.getProofs() == null
														? List.of()
														: review.getProofs().stream()
																  .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
																  .toList();
		
		boolean hasProof = !activeProofs.isEmpty();
		boolean anyVerified = activeProofs.stream()
													 .anyMatch(p -> Boolean.TRUE.equals(p.getVerified()));
		
		Long userId = review.getUser() != null ? review.getUser().getId() : null;
		String userName = review.getUser() != null ? review.getUser().getDisplayName() : null;
		
		Long flatId = review.getFlat() != null ? review.getFlat().getId() : null;
		String flatAddress = review.getFlat() != null && review.getFlat().getAddress() != null
										? review.getFlat().getAddress().getAddressLine()
										: null;
		
		return ReviewDto.builder()
							 .id(review.getId())
							 .title(review.getTitle())
							 .content(review.getContent())
							 .rating(review.getRating())
							 .status(review.getStatus() != null ? review.getStatus().name() : null)
							 .createdAt(review.getCreatedAt())
							 .userId(userId)
							 .userName(userName)
							 .flatId(flatId)
							 .flatAddress(flatAddress)
							 .hasProof(hasProof)
							 .verifiedStay(hasProof && anyVerified
													&& review.getStatus() == ReviewStatus.APPROVED)
							 .build();
	}
	
	public static ReviewProofDto toProofDto(ReviewProof proof) {
		if (proof == null) return null;
		return ReviewProofDto.builder()
									.id(proof.getId())
									.originalFilename(proof.getOriginalFilename())
									.contentType(proof.getContentType())
									.sizeBytes(proof.getSizeBytes())
									.displayOrder(proof.getDisplayOrder())
									.verified(proof.getVerified())
									.build();
	}
	
	public static List<ReviewProofDto> toProofDtos(List<ReviewProof> proofs) {
		if (proofs == null) return List.of();
		return proofs.stream().map(ReviewMapper::toProofDto).toList();
	}
	
}