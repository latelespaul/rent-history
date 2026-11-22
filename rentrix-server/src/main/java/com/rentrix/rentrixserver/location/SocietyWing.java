package com.rentrix.rentrixserver.location;

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

/** A wing of a tower, e.g. "Wing A" (§5.18). */
@Entity
@Table(name = "society_wings")
@Getter
@Setter
public class SocietyWing extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tower_id", nullable = false)
	private SocietyTower tower;
	
	@Column(name = "name", nullable = false, length = 60)
	private String name;
	
	@Column(name = "name_key", nullable = false, length = 60)
	private String nameKey;
	
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private LocationStatus status = LocationStatus.ACTIVE;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;
	
	public void setName(String name) {
		this.name = LocationNames.clean(name);
		this.nameKey = LocationNames.key(name);
	}
	
}
