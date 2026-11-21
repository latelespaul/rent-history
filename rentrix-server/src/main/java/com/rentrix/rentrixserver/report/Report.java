package com.rentrix.rentrixserver.report;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
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

import java.time.Instant;

/** An abuse report on a review, response, user, property or tenancy (§5.11, D-49). */
@Entity
@Table(name = "reports")
@Getter
@Setter
public class Report extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_id", nullable = false)
	private User reporter;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "target_type", nullable = false, length = 24)
	private ReportTargetType targetType;
	
	/** Internal id of the reported row; the table is given by {@link #targetType}. */
	@Column(name = "target_id", nullable = false)
	private Long targetId;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "reason", nullable = false, length = 32)
	private ReportReason reason;
	
	@Column(name = "details", length = 1000)
	private String details;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private ReportStatus status = ReportStatus.OPEN;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "handled_by")
	private User handledBy;
	
	@Column(name = "resolution_note", length = 1000)
	private String resolutionNote;
	
	@Column(name = "handled_at")
	private Instant handledAt;
	
}
