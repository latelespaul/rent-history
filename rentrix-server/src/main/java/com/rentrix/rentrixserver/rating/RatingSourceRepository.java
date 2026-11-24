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
 * {@code counts_in_aggregate = true} (§9); with {@code verifiedOnly} the totals also need the green tick,
 * {@code verification_status = VERIFIED} (D-135, {@link RatingProperties}).
 */
interface RatingSourceRepository extends Repository<Review, Long> {
	
	String TOTALS = "select count(r), coalesce(sum(r.ratingOverall), 0), "
		+ "sum(case when r.ratingOverall = 1 then 1 else 0 end), sum(case when r.ratingOverall = 2 then 1 else 0 end), "
		+ "sum(case when r.ratingOverall = 3 then 1 else 0 end), sum(case when r.ratingOverall = 4 then 1 else 0 end), "
		+ "sum(case when r.ratingOverall = 5 then 1 else 0 end), avg(r.ratingA), avg(r.ratingB), avg(r.ratingC), "
		+ "max(r.publishedAt) from Review r ";
	
	/** The green-tick filter when {@code verifiedOnly} is set (D-135). */
	String VERIFIED_ONLY = "and (:verifiedOnly = false "
		+ "or r.verificationStatus = com.rentrix.rentrixserver.verification.VerificationState.VERIFIED)";
	
	/** Counted reviews of one subject: a person in one direction, or a property (D-158 shares the §9 rules). */
	String COUNTED = "r.direction = :direction and r.status = :status and r.countsInAggregate = true " + VERIFIED_ONLY;
	
	/** Per parameter: rating count and sum (D-158). */
	String CRITERIA = "select key(c), count(c), sum(value(c)) from Review r join r.criteria c ";
	
	/** "Would you recommend": answers and yeses (D-158). */
	String RECOMMEND = "select count(r.wouldRecommend), "
		+ "coalesce(sum(case when r.wouldRecommend = true then 1 else 0 end), 0) from Review r ";
	
	/** Totals of a person subject (index ix_reviews_subject_user). */
	@Query(TOTALS + "where r.subjectUser.id = :userId and r.direction = :direction and r.status = :status "
		+ "and r.countsInAggregate = true " + VERIFIED_ONLY)
	List<Object[]> personTotals(@Param("userId") Long userId, @Param("direction") ReviewDirection direction,
		@Param("status") ReviewStatus status, @Param("verifiedOnly") boolean verifiedOnly);
	
	/** Totals of a property (index ix_reviews_property). */
	@Query(TOTALS + "where r.property.id = :propertyId and r.direction = :direction and r.status = :status "
		+ "and r.countsInAggregate = true " + VERIFIED_ONLY)
	List<Object[]> propertyTotals(@Param("propertyId") Long propertyId, @Param("direction") ReviewDirection direction,
		@Param("status") ReviewStatus status, @Param("verifiedOnly") boolean verifiedOnly);
	
	@Query(CRITERIA + "where r.subjectUser.id = :userId and " + COUNTED + " group by key(c)")
	List<Object[]> personCriteria(@Param("userId") Long userId, @Param("direction") ReviewDirection direction,
		@Param("status") ReviewStatus status, @Param("verifiedOnly") boolean verifiedOnly);
	
	@Query(CRITERIA + "where r.property.id = :propertyId and " + COUNTED + " group by key(c)")
	List<Object[]> propertyCriteria(@Param("propertyId") Long propertyId, @Param("direction") ReviewDirection direction,
		@Param("status") ReviewStatus status, @Param("verifiedOnly") boolean verifiedOnly);
	
	@Query(RECOMMEND + "where r.subjectUser.id = :userId and " + COUNTED)
	List<Object[]> personRecommend(@Param("userId") Long userId, @Param("direction") ReviewDirection direction,
		@Param("status") ReviewStatus status, @Param("verifiedOnly") boolean verifiedOnly);
	
	@Query(RECOMMEND + "where r.property.id = :propertyId and " + COUNTED)
	List<Object[]> propertyRecommend(@Param("propertyId") Long propertyId, @Param("direction") ReviewDirection direction,
		@Param("status") ReviewStatus status, @Param("verifiedOnly") boolean verifiedOnly);
	
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
