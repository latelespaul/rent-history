package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.city.City;
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

/** A gated community or apartment complex inside one area, e.g. "Dream Acres · by Sobha" (§5.18). */
@Entity
@Table(name = "societies")
@Getter
@Setter
public class Society extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "area_id", nullable = false)
	private Area area;
	
	/** Copy of the area's city, for city-wide browse; set by {@link #setArea}. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "city_code", nullable = false)
	private City city;
	
	@Column(name = "name", nullable = false, length = 160)
	private String name;
	
	/** See {@link LocationNames#key}; unique per area. */
	@Column(name = "name_key", nullable = false, length = 160)
	private String nameKey;
	
	@Column(name = "developer", length = 120)
	private String developer;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "kind", nullable = false, length = 24)
	private SocietyKind kind;
	
	@Column(name = "street", length = 200)
	private String street;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private LocationStatus status = LocationStatus.ACTIVE;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;
	
	@Column(name = "is_demo", nullable = false)
	private boolean demo;
	
	/** Sets the area and the city copied from it. */
	public void setArea(Area area) {
		this.area = area;
		this.city = area == null ? null : area.getCity();
	}
	
	public void setName(String name) {
		this.name = LocationNames.clean(name);
		this.nameKey = LocationNames.key(name);
	}
	
}
