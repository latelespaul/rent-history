package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import com.rentrix.rentrixserver.report.Report;
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
 * Append-only audit log entry for every admin action and decision (§5.11, D-51, D-87). Rows are never updated or
 * deleted; write them through {@link AuditLog}.
 */
@Entity
@Table(name = "moderation_actions")
@Getter
@Setter
public class ModerationAction extends BaseEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "admin_id", nullable = false)
	private User admin;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "action", nullable = false, length = 32)
	private ModerationActionType action;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "target_type", nullable = false, length = 24)
	private ModerationTargetType targetType;
	
	/** Internal id for joins; null for targets without one (cities). */
	@Column(name = "target_id")
	private Long targetId;
	
	/** The target's {@code public_id}, or the city code for {@code CITY}. */
	@Column(name = "target_ref", nullable = false, length = 36)
	private String targetRef;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "report_id")
	private Report report;
	
	@Column(name = "reason", length = 1000)
	private String reason;
	
}
