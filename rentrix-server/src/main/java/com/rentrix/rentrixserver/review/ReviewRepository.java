package com.rentrix.rentrixserver.review;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	Optional<Review> findByPublicId(UUID publicId);

	/** The review row locked for an edit, withdrawal or response. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Review r where r.publicId = :publicId")
	Optional<Review> lockByPublicId(@Param("publicId") UUID publicId);

	Optional<Review> findByTenancyIdAndDirection(Long tenancyId, ReviewDirection direction);

	List<Review> findByTenancyId(Long tenancyId);

	/** Reviews about a person as landlord or as tenant (index ix_reviews_subject_user). */
	Page<Review> findBySubjectUserIdAndDirectionAndStatus(Long subjectUserId, ReviewDirection direction,
		ReviewStatus status, Pageable pageable);

	/** {@code /me/reviews/received}: published ones and those still awaiting reveal. */
	Page<Review> findBySubjectUserIdAndDirectionInAndStatusIn(Long subjectUserId, Collection<ReviewDirection> directions,
		Collection<ReviewStatus> statuses, Pageable pageable);

	/** Reviews about a property (index ix_reviews_property). */
	Page<Review> findByPropertyIdAndDirectionAndStatus(Long propertyId, ReviewDirection direction,
		ReviewStatus status, Pageable pageable);

	/** {@code /me/reviews/written} (index ix_reviews_author). */
	Page<Review> findByAuthorIdAndStatusIn(Long authorId, Collection<ReviewStatus> statuses, Pageable pageable);

	/** D-45: whether the author ever submitted a review (the first one needs a 7-day-old account). */
	boolean existsByAuthorId(Long authorId);
	
	/** Whether this author reviewed anything on this tenancy (the "next steps" after adding a home). */
	boolean existsByTenancyIdAndAuthorId(Long tenancyId, Long authorId);

	/** Whether this tenancy has a review in this direction and state (the author's "See my review" link). */
	boolean existsByTenancyIdAndDirectionAndStatus(Long tenancyId, ReviewDirection direction, ReviewStatus status);
	
	/** D-45 volume guard: reviews an author submitted since a moment, oldest first. */
	List<Review> findByAuthorIdAndSubmittedAtAfterOrderBySubmittedAtAsc(Long authorId, Instant since);

	/**
	 * Reveal job (D-46): person reviews still hidden whose 14 days passed. Index ix_reviews_status_submitted.
	 */
	@Query("select r.publicId from Review r where r.status = :status and r.submittedAt < :submittedBefore")
	List<UUID> findRevealDue(@Param("status") ReviewStatus status, @Param("submittedBefore") Instant submittedBefore);

	/** Source rows for recomputing a person aggregate (D-33). */
	List<Review> findBySubjectUserIdAndDirectionAndStatusAndCountsInAggregateTrue(Long subjectUserId,
		ReviewDirection direction, ReviewStatus status);

	/** Source rows for recomputing a property aggregate (D-33). */
	List<Review> findByPropertyIdAndDirectionAndStatusAndCountsInAggregateTrue(Long propertyId,
		ReviewDirection direction, ReviewStatus status);

}
