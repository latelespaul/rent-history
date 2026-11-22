package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** A tower or block of a society, e.g. "Tower 1" (§5.18). It may have no wings. */
@Entity
@Table(name = "society_towers")
@Getter
@Setter
public class SocietyTower extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "society_id", nullable = false)
	private Society society;
	
	@Column(name = "name", nullable = false, length = 60)
	private String name;
	
	@Column(name = "name_key", nullable = false, length = 60)
	private String nameKey;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "kind", nullable = false, length = 8)
	private TowerKind kind = TowerKind.TOWER;
	
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private LocationStatus status = LocationStatus.ACTIVE;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;
	
	@OneToMany(mappedBy = "tower", cascade = CascadeType.ALL)
	@OrderBy("sortOrder, nameKey")
	@BatchSize(size = 50)
	private List<SocietyWing> wings = new ArrayList<>();
	
	public void setName(String name) {
		this.name = LocationNames.clean(name);
		this.nameKey = LocationNames.key(name);
	}
	
	/** Adds a wing at the end of this tower's order. */
	public SocietyWing addWing(String name, LocationStatus status, User createdBy) {
		SocietyWing wing = new SocietyWing();
		wing.setTower(this);
		wing.setName(name);
		wing.setSortOrder(wings.size());
		wing.setStatus(status);
		wing.setCreatedBy(createdBy);
		wings.add(wing);
		return wing;
	}
	
}
