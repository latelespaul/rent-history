package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The previous version of a review, kept on every edit (§5.7); readable by admins. */
@Entity
@Table(name = "review_revisions")
@Getter
@Setter
public class ReviewRevision extends BaseEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_id", nullable = false)
	private Review review;
	
	@Column(name = "revision_no", nullable = false)
	private int revisionNo;
	
	@Column(name = "rating_overall", nullable = false)
	private int ratingOverall;
	
	@Column(name = "rating_a")
	private Integer ratingA;
	
	@Column(name = "rating_b")
	private Integer ratingB;
	
	@Column(name = "rating_c")
	private Integer ratingC;
	
	@Column(name = "body", nullable = false, length = 2000)
	private String body;
	
}
