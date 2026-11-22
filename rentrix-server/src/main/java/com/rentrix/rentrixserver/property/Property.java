package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.location.SocietyWing;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.verification.VerificationState;
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
	
	/**
	 * Place in the location hierarchy (D-107, D-110): the area is required by the API (nullable only in the DDL,
	 * D-114); society, tower and wing are all null for a standalone house or building. Set through
	 * {@code LocationService.resolve}, which checks the placement rules.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "area_id")
	private Area area;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "society_id")
	private Society society;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tower_id")
	private SocietyTower tower;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "wing_id")
	private SocietyWing wing;
	
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

	/** The green tick (D-134); null reads as {@code UNVERIFIED}. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "verification_status", length = 16)
	private VerificationState verificationStatus;
	
	/** The tenant who added this home while its landlord was not on the platform (D-138); null for listings. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "added_by")
	private User addedBy;
	
	/** The owner as the tenant named them (D-138): private to {@link #addedBy} and admins, for a later claim. */
	@Column(name = "owner_name", length = 80)
	private String ownerName;
	
	@Column(name = "owner_email", length = 320)
	private String ownerEmail;
	
	@Column(name = "owner_phone", length = 20)
	private String ownerPhone;
	
	/** Who sees {@link #getUnitLabel()} (D-139); {@code MASKED} keeps D-57, tenant-added homes are {@code OPEN}. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "unit_label_visibility", nullable = false, length = 16)
	private FieldVisibility unitLabelVisibility = FieldVisibility.MASKED;
	
	/** Who sees the owner's name and contact (D-139); always {@code PRIVATE} today. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "owner_contact_visibility", nullable = false, length = 16)
	private FieldVisibility ownerContactVisibility = FieldVisibility.PRIVATE;
	
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
