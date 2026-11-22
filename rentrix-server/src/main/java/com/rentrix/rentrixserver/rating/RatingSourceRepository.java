package com.rentrix.rentrixserver.rating;

import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read side of the rating recompute (D-33): totals over counted source reviews, the repeat-pair groups (D-44) and
 * the subject lists of the nightly repair. Counted means {@code status = PUBLISHED} and
 * {@code counts_in_aggregate = true} (§9); the headline totals also need the green tick,
 * {@code verification_status = VERIFIED} (D-135).
 */
interface RatingSourceRepository extends Repository<Review, Long> {
	
	String TOTALS = "select count(r), coalesce(sum(r.ratingOverall), 0), "
		+ "sum(case when r.ratingOverall = 1 then 1 else 0 end), sum(case when r.ratingOverall = 2 then 1 else 0 end), "
		+ "sum(case when r.ratingOverall = 3 then 1 else 0 end), sum(case when r.ratingOverall = 4 then 1 else 0 end), "
		+ "sum(case when r.ratingOverall = 5 then 1 else 0 end), avg(r.ratingA), avg(r.ratingB), avg(r.ratingC), "
		+ "max(r.publishedAt) from Review r ";
	
	/** The green-tick state, as a JPQL enum literal (D-135). */
	String VERIFIED = "com.rentrix.rentrixserver.verification.VerificationState.VERIFIED";
	
	/** Totals of a person subject (index ix_reviews_subject_user). */
	@Query(TOTALS + "where r.subjectUser.id = :userId and r.direction = :direction and r.status = :status "
		+ "and r.countsInAggregate = true and r.verificationStatus = " + VERIFIED)
	List<Object[]> personTotals(@Param("userId") Long userId, @Param("direction") ReviewDirection direction,
		@Param("status") ReviewStatus status);
	
	/** Totals of a property (index ix_reviews_property). */
	@Query(TOTALS + "where r.property.id = :propertyId and r.direction = :direction and r.status = :status "
		+ "and r.countsInAggregate = true and r.verificationStatus = " + VERIFIED)
	List<Object[]> propertyTotals(@Param("propertyId") Long propertyId, @Param("direction") ReviewDirection direction,
		@Param("status") ReviewStatus status);
	
	/** Person reviews of one landlord–tenant pair in one direction, across all their tenancies (D-44). */
	@Query("select r from Review r join fetch r.tenancy t where r.direction = :direction "
		+ "and t.landlord.id = :landlordId and t.tenant.id = :tenantId")
	List<Review> pairGroup(@Param("direction") ReviewDirection direction, @Param("landlordId") Long landlordId,
		@Param("tenantId") Long tenantId);
	
	/** Property reviews one tenant wrote about one property, across their tenancies there (D-44). */
	@Query("select r from Review r join fetch r.tenancy t where r.direction = :direction "
		+ "and r.property.id = :propertyId and r.author.id = :authorId")
	List<Review> propertyGroup(@Param("direction") ReviewDirection direction, @Param("propertyId") Long propertyId,
		@Param("authorId") Long authorId);
	
	/** Every review, for the nightly repair of the counting flags. */
	@Query("select r from Review r join fetch r.tenancy t")
	List<Review> allWithTenancy();
	
	@Query("select distinct r.subjectUser.id from Review r where r.direction = :direction and r.subjectUser is not null")
	List<Long> personSubjects(@Param("direction") ReviewDirection direction);
	
	@Query("select distinct r.property.id from Review r where r.direction = :direction")
	List<Long> propertySubjects(@Param("direction") ReviewDirection direction);
	
	@Query("select a.id.subjectId from RatingAggregate a where a.id.subjectType = :type")
	List<Long> storedSubjects(@Param("type") RatingSubjectType type);
	
	/** Count and rating sum of every stored aggregate of one subject type: the prior mean of D-53. */
	@Query("select coalesce(sum(a.reviewCount), 0), coalesce(sum(a.ratingSum), 0) from RatingAggregate a "
		+ "where a.id.subjectType = :type")
	List<Object[]> typeTotals(@Param("type") RatingSubjectType type);
	
}
