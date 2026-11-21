package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** The one public reply a review's subject (or the property's landlord) may post (§5.7). */
@Entity
@Table(name = "review_responses")
@Getter
@Setter
public class ReviewResponse extends PublicEntity {
	
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_id", nullable = false, unique = true)
	private Review review;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;
	
	@Column(name = "body", nullable = false, length = 1000)
	private String body;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private ResponseStatus status = ResponseStatus.PUBLISHED;
	
	@Column(name = "edit_until")
	private Instant editUntil;
	
	@Column(name = "hidden_at")
	private Instant hiddenAt;
	
	@Column(name = "hidden_reason", length = 500)
	private String hiddenReason;
	
}
