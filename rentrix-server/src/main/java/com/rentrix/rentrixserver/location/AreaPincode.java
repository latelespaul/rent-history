package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One PIN code of an area; the same PIN code may belong to several areas (D-108). */
@Entity
@Table(name = "area_pincodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AreaPincode extends TimestampedEntity {
	
	@EmbeddedId
	private Key id;
	
	@MapsId("areaId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "area_id", nullable = false)
	private Area area;
	
	AreaPincode(Area area, String pincode) {
		this.area = area;
		this.id = new Key(null, pincode);
	}
	
	public String getPincode() {
		return id.getPincode();
	}
	
	@Embeddable
	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class Key implements java.io.Serializable {
		
		@Column(name = "area_id", nullable = false)
		private Long areaId;
		
		@JdbcTypeCode(SqlTypes.CHAR)
		@Column(name = "pincode", nullable = false, length = 6)
		private String pincode;
		
		Key(Long areaId, String pincode) {
			this.areaId = areaId;
			this.pincode = pincode;
		}
		
		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && java.util.Objects.equals(areaId, key.areaId)
				&& java.util.Objects.equals(pincode, key.pincode);
		}
		
		@Override
		public int hashCode() {
			return java.util.Objects.hash(areaId, pincode);
		}
		
	}
	
}
