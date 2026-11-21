package com.rentrix.rentrixserver.tenancy;

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

import java.time.LocalDate;

/** Monthly rent from a month on (§5.5). Only CONFIRMED rows appear in public rent history (D-25). */
@Entity
@Table(name = "rent_records")
@Getter
@Setter
public class RentRecord extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenancy_id", nullable = false)
	private Tenancy tenancy;
	
	/** First day of the month the rent applies from. */
	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;
	
	@Column(name = "monthly_rent_inr", nullable = false)
	private long monthlyRentInr;
	
	@Column(name = "maintenance_inr")
	private Long maintenanceInr;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "added_by", nullable = false)
	private User addedBy;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private RentRecordStatus status = RentRecordStatus.PROPOSED;
	
}
