package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.city.City;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A locality of a city, e.g. Koramangala, with its PIN codes (§5.17, D-108). */
@Entity
@Table(name = "areas")
@Getter
@Setter
public class Area extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "city_code", nullable = false)
	private City city;
	
	@Column(name = "name", nullable = false, length = 120)
	private String name;
	
	/** See {@link LocationNames#key}; unique per city. */
	@Column(name = "name_key", nullable = false, length = 120)
	private String nameKey;
	
	/** Comma-separated other spellings that search also matches, e.g. "Kormangala". */
	@Column(name = "aliases")
	private String aliases;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private LocationStatus status = LocationStatus.ACTIVE;
	
	/** The proposer of a {@code PENDING} row, or the admin who created it; null for seed rows. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;
	
	@Column(name = "is_demo", nullable = false)
	private boolean demo;
	
	@OneToMany(mappedBy = "area", cascade = CascadeType.ALL, orphanRemoval = true)
	@BatchSize(size = 50)
	private List<AreaPincode> pincodes = new ArrayList<>();
	
	/** Sets the name and its unique key together. */
	public void setName(String name) {
		this.name = LocationNames.clean(name);
		this.nameKey = LocationNames.key(name);
	}
	
	public void addPincode(String pincode) {
		if (pincodeList().contains(pincode)) {
			return;
		}
		pincodes.add(new AreaPincode(this, pincode));
	}
	
	public List<String> pincodeList() {
		return pincodes.stream().map(AreaPincode::getPincode).sorted().toList();
	}
	
	public List<String> aliasList() {
		if (aliases == null || aliases.isBlank()) {
			return List.of();
		}
		return Arrays.stream(aliases.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
	
}
