package com.rentrix.rentrixserver.property;

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

import java.time.Instant;
import java.util.Locale;

/** A rentable unit owned by one landlord (§5.3). Only PUBLISHED rows are visible to others (D-21). */
@Entity
@Table(name = "properties")
@Getter
@Setter
public class Property extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "landlord_id", nullable = false)
	private User landlord;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "city_code", nullable = false)
	private City city;
	
	@Column(name = "locality", nullable = false, length = 120)
	private String locality;
	
	/** Search key derived from {@link #locality}; see {@link #localityKey(String)}. */
	@Column(name = "locality_key", nullable = false, length = 120)
	private String localityKey;
	
	@Column(name = "building_name", length = 160)
	private String buildingName;
	
	@Column(name = "street", length = 200)
	private String street;
	
	/** Flat or house number; private to the parties and admins (D-57). */
	@Column(name = "unit_label", length = 40)
	private String unitLabel;
	
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "pincode", nullable = false, length = 6)
	private String pincode;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "property_type", nullable = false, length = 24)
	private PropertyType propertyType;
	
	@Column(name = "bedrooms")
	private Integer bedrooms;
	
	@Column(name = "bathrooms")
	private Integer bathrooms;
	
	@Column(name = "area_sqft")
	private Integer areaSqft;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "furnishing", length = 16)
	private Furnishing furnishing;
	
	@Column(name = "parking")
	private Boolean parking;
	
	@Column(name = "description", length = 2000)
	private String description;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private PropertyStatus status = PropertyStatus.DRAFT;
	
	@Column(name = "is_demo", nullable = false)
	private boolean demo;
	
	@Column(name = "deleted_at")
	private Instant deletedAt;
	
	/** Sets the locality and its search key together. */
	public void setLocality(String locality) {
		this.locality = locality == null ? null : locality.trim();
		this.localityKey = localityKey(locality);
	}
	
	/** Lower-cased, trimmed, single-spaced form used by locality search. */
	public static String localityKey(String locality) {
		return locality == null ? null : locality.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}
	
}
