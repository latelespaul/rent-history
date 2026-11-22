package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only history of one application (§5.13): every submit by the user and every decision by an admin, with
 * the revision it applied to. Admin events are also written to {@code moderation_actions} (D-87).
 */
@Entity
@Table(name = "verification_events")
@Getter
@Setter
public class VerificationEvent extends BaseEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false, updatable = false)
	private VerificationApplication application;
	
	@Column(name = "revision", nullable = false, updatable = false)
	private int revision;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "event", nullable = false, updatable = false, length = 24)
	private VerificationEventType event;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_id", nullable = false, updatable = false)
	private User actor;
	
	/** The decision code (reject, revoke) or the identity result ({@code IDENTITY_CHECKED}). */
	@Column(name = "code", length = 32, updatable = false)
	private String code;
	
	@Column(name = "note", length = 1000, updatable = false)
	private String note;
	
}
