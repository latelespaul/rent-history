package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A re-encoded property photo plus thumbnail behind the ImageStorage interface (§15). */
@Entity
@Table(name = "property_images")
@Getter
@Setter
public class PropertyImage extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;
	
	@Column(name = "storage_key", nullable = false)
	private String storageKey;
	
	@Column(name = "thumb_key", nullable = false)
	private String thumbKey;
	
	@Column(name = "content_type", nullable = false, length = 64)
	private String contentType;
	
	@Column(name = "width", nullable = false)
	private int width;
	
	@Column(name = "height", nullable = false)
	private int height;
	
	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;
	
	@Column(name = "display_order", nullable = false)
	private int displayOrder;
	
	@Column(name = "is_primary", nullable = false)
	private boolean primary;
	
}
