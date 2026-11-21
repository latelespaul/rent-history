package com.rentrix.rentrixserver.city;

import com.rentrix.rentrixserver.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

/** Reference row per city (D-20). v1 ships BLR and PNQ, both live. */
@Entity
@Table(name = "cities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class City extends TimestampedEntity {
	
	@Id
	@Column(name = "code", length = 8)
	private String code;
	
	@Column(name = "name", nullable = false, length = 80)
	private String name;
	
	/** Comma-separated other names that search also matches, e.g. "Bengaluru". */
	@Column(name = "aliases")
	private String aliases;
	
	@Column(name = "state", nullable = false, length = 80)
	private String state;
	
	@Column(name = "live", nullable = false)
	private boolean live;
	
	public List<String> aliasList() {
		if (aliases == null || aliases.isBlank()) {
			return List.of();
		}
		return Arrays.stream(aliases.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
	
}
