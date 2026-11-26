package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.verification.VerificationState;
import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A rating tied to a confirmed tenancy (§5.6). At most one per direction per tenancy (D-38).
 * Sub-ratings a/b/c mean different things per direction (D-29). A review written with the form (D-157) has a 1–5
 * rating per parameter it rated in {@link #criteria}, and may have "would you recommend", pros and cons.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
public class Review extends PublicEntity {
	
	/** A random id (D-169): a UUIDv7 would tell anyone who sees the review when it was written, to the millisecond. */
	public Review() {
		useRandomPublicId();
	}
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenancy_id", nullable = false)
	private Tenancy tenancy;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "direction", nullable = false, length = 24)
	private ReviewDirection direction;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;
	
	/** The reviewed landlord or tenant; null for TENANT_TO_PROPERTY. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subject_user_id")
	private User subjectUser;
	
	/** Always set (denormalised from the tenancy for queries). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;
	
	@Column(name = "rating_overall", nullable = false)
	private int ratingOverall;
	
	@Column(name = "rating_a")
	private Integer ratingA;
	
	@Column(name = "rating_b")
	private Integer ratingB;
	
	@Column(name = "rating_c")
	private Integer ratingC;
	
	/** D-157: the parameters the author rated, 1–5 each; empty for a review written before the form. */
	@ElementCollection
	@CollectionTable(name = "review_criterion_ratings", joinColumns = @JoinColumn(name = "review_id"))
	@MapKeyColumn(name = "criterion", length = 32)
	@MapKeyEnumerated(EnumType.STRING)
	@Column(name = "rating", nullable = false)
	@BatchSize(size = 50)
	private Map<ReviewCriterion, Integer> criteria = new HashMap<>();
	
	/** D-157: written with the review form, so the comment stays optional on every edit. */
	@Column(name = "form_review", nullable = false)
	private boolean formReview;
	
	/** D-157: "would you recommend"; null when not answered. */
	@Column(name = "would_recommend")
	private Boolean wouldRecommend;
	
	@Column(name = "pros", length = 1000)
	private String pros;
	
	@Column(name = "cons", length = 1000)
	private String cons;
	
	/** The text; empty for a form review without a comment (D-157). */
	@Column(name = "body", nullable = false, length = 2000)
	private String body;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 24)
	private ReviewStatus status = ReviewStatus.AWAITING_REVEAL;
	
	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;
	
	@Column(name = "published_at")
	private Instant publishedAt;
	
	@Column(name = "edit_until")
	private Instant editUntil;
	
	@Column(name = "last_edited_at")
	private Instant lastEditedAt;
	
	/** D-40: the one update allowed after the tenancy ended has been used. */
	@Column(name = "post_end_update_used", nullable = false)
	private boolean postEndUpdateUsed;
	
	@Column(name = "hidden_at")
	private Instant hiddenAt;
	
	@Column(name = "hidden_reason", length = 500)
	private String hiddenReason;
	
	/** D-50: text hidden pending a PERSONAL_DATA report; the rating still counts. */
	@Column(name = "text_hidden_at")
	private Instant textHiddenAt;
	
	/** False for superseded repeat-pair reviews (D-44) and hidden reviews. */
	@Column(name = "counts_in_aggregate", nullable = false)
	private boolean countsInAggregate = true;
	
	@Column(name = "deleted_at")
	private Instant deletedAt;
	
	/** The green tick (D-134); null reads as {@code UNVERIFIED}. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "verification_status", length = 16)
	private VerificationState verificationStatus;
	
	/** The admin who gave or refused the tick, when and why (D-134). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "verification_decided_by")
	private User verificationDecidedBy;
	
	@Column(name = "verification_decided_at")
	private Instant verificationDecidedAt;
	
	@Column(name = "verification_reason", length = 1000)
	private String verificationReason;
	
}
