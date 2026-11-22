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

/** Reference row per city (D-20). Cities are data: adding one is a row, activating it sets {@code live}. */
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
	
	/** Comma-separated pincode prefixes, e.g. "56"; empty accepts any 6-digit pincode (D-84). */
	@Column(name = "pincode_prefixes", length = 64)
	private String pincodePrefixes;
	
	/** Optional group the web shows these cities under, e.g. "Delhi NCR" for Delhi, Gurugram and Noida. */
	@Column(name = "region", length = 80)
	private String region;
	
	public List<String> aliasList() {
		return splitList(aliases);
	}
	
	public List<String> pincodePrefixList() {
		return splitList(pincodePrefixes);
	}
	
	private static List<String> splitList(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
	
}
