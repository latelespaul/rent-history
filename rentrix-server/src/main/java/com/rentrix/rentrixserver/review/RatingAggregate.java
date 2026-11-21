package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stored rating summary per subject (§5.10). Recomputed from source reviews in the same
 * transaction as any counting change, never incremented (D-33).
 */
@Entity
@Table(name = "rating_aggregates")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RatingAggregate extends TimestampedEntity {
	
	@EmbeddedId
	private RatingAggregateId id;
	
	@Column(name = "review_count", nullable = false)
	private int reviewCount;
	
	@Column(name = "rating_sum", nullable = false)
	private int ratingSum;
	
	@Column(name = "average", precision = 3, scale = 2)
	private BigDecimal average;
	
	@Column(name = "bayes_score", precision = 4, scale = 3)
	private BigDecimal bayesScore;
	
	@Column(name = "count_1", nullable = false)
	private int count1;
	
	@Column(name = "count_2", nullable = false)
	private int count2;
	
	@Column(name = "count_3", nullable = false)
	private int count3;
	
	@Column(name = "count_4", nullable = false)
	private int count4;
	
	@Column(name = "count_5", nullable = false)
	private int count5;
	
	@Column(name = "sub_a_avg", precision = 3, scale = 2)
	private BigDecimal subAAvg;
	
	@Column(name = "sub_b_avg", precision = 3, scale = 2)
	private BigDecimal subBAvg;
	
	@Column(name = "sub_c_avg", precision = 3, scale = 2)
	private BigDecimal subCAvg;
	
	@Column(name = "last_review_at")
	private Instant lastReviewAt;
	
	public RatingAggregate(RatingSubjectType subjectType, Long subjectId) {
		this.id = new RatingAggregateId(subjectType, subjectId);
	}
	
}
