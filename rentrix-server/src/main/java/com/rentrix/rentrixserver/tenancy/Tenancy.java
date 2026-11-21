package com.rentrix.rentrixserver.tenancy;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.property.Property;
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
import java.time.LocalDate;

/**
 * One tenant on one property for one continuous period (§5.4, D-23). The trust anchor:
 * reviews and public rent history need a tenancy both parties confirmed (D-34).
 */
@Entity
@Table(name = "tenancies")
@Getter
@Setter
public class Tenancy extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;
	
	/** The property's landlord when the tenancy was created (D-22). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "landlord_id", nullable = false)
	private User landlord;
	
	/** Null while a landlord invite waits for a tenant who has no account yet. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tenant_id")
	private User tenant;
	
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;
	
	/** Null while the tenancy is current. */
	@Column(name = "end_date")
	private LocalDate endDate;
	
	@Column(name = "initial_rent_inr", nullable = false)
	private long initialRentInr;
	
	@Column(name = "deposit_inr")
	private Long depositInr;
	
	@Column(name = "deposit_refunded_inr")
	private Long depositRefundedInr;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 24)
	private TenancyStatus status = TenancyStatus.PENDING_CONFIRMATION;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "initiated_by", nullable = false, length = 16)
	private TenancyParty initiatedBy;
	
	@Column(name = "invite_email", length = 320)
	private String inviteEmail;
	
	@Column(name = "decline_reason", length = 500)
	private String declineReason;
	
	/** Null until confirmed. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "evidence_level", length = 16)
	private EvidenceLevel evidenceLevel;
	
	@Column(name = "confirmed_at")
	private Instant confirmedAt;
	
	@Column(name = "ended_at")
	private Instant endedAt;
	
	@Column(name = "voided_at")
	private Instant voidedAt;
	
	@Column(name = "void_reason", length = 500)
	private String voidReason;
	
}
