package com.rentrix.rentrixserver.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, Long> {
	
	Optional<Review> findByPublicId(UUID publicId);
	
	Optional<Review> findByTenancyIdAndDirection(Long tenancyId, ReviewDirection direction);
	
	/** Reviews about a person as landlord or as tenant (index ix_reviews_subject_user). */
	Page<Review> findBySubjectUserIdAndDirectionAndStatus(Long subjectUserId, ReviewDirection direction,
		ReviewStatus status, Pageable pageable);
	
	/** Reviews about a property (index ix_reviews_property). */
	Page<Review> findByPropertyIdAndDirectionAndStatus(Long propertyId, ReviewDirection direction,
		ReviewStatus status, Pageable pageable);
	
	/** Source rows for recomputing a person aggregate (D-33). */
	List<Review> findBySubjectUserIdAndDirectionAndStatusAndCountsInAggregateTrue(Long subjectUserId,
		ReviewDirection direction, ReviewStatus status);
	
	/** Source rows for recomputing a property aggregate (D-33). */
	List<Review> findByPropertyIdAndDirectionAndStatusAndCountsInAggregateTrue(Long propertyId,
		ReviewDirection direction, ReviewStatus status);
	
}
