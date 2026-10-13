package com.rentrix.rentrixserver.repository;

import com.rentrix.rentrixserver.entity.Review;
import com.rentrix.rentrixserver.entity.constants.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
	
	@EntityGraph(attributePaths = {"user", "user.userDetail", "flat", "flat.address"})
	Page<Review> findByFlatIdAndStatus(Long flatId, ReviewStatus status, Pageable pageable);
	
	@EntityGraph(attributePaths = {"user", "user.userDetail", "flat", "flat.address"})
	Page<Review> findByUserId(Long userId, Pageable pageable);
	
	@EntityGraph(attributePaths = {"user", "user.userDetail", "flat", "flat.address"})
	Page<Review> findByStatus(ReviewStatus status, Pageable pageable);
	
	// use this method to fetch all reviews with relations for admin moderation, with pagination
	// instead of findAll() which may cause N+1 problem
	@EntityGraph(attributePaths = {"user", "user.userDetail", "flat", "flat.address"})
	@Query("SELECT r FROM Review r")
	Page<Review> findAllWithRelations(Pageable pageable);
	
	boolean existsByUserIdAndFlatId(Long userId, Long flatId);
	
	@EntityGraph(attributePaths = {"user", "user.userDetail", "flat", "flat.address", "proofs"})
	@Query("SELECT r FROM Review r WHERE r.id = :id")
	Optional<Review> findByIdWithRelations(@Param("id") Long id);
	
	/**
	 * Batch aggregates for a set of flat IDs.
	 * Returns rows of [flatId (Long), averageRating (Double), reviewCount (Long)].
	 * Only APPROVED reviews contribute.
	 */
	@Query("""
		SELECT r.flat.id, AVG(r.rating), COUNT(r)
		FROM Review r
		WHERE r.flat.id IN :flatIds AND r.status = :status AND r.deleted = false
		GROUP BY r.flat.id
		""")
	List<Object[]> findRatingAggregates(
		@Param("flatIds") List<Long> flatIds,
		@Param("status") ReviewStatus status
	);
	
}