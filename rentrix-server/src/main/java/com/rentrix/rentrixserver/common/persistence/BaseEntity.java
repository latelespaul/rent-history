package com.rentrix.rentrixserver.common.persistence;

import com.rentrix.rentrixserver.common.id.TimeOrderedId;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/** Tables with an internal BIGINT id (D-12). The id is never exposed by the API. */
@MappedSuperclass
@Getter
public abstract class BaseEntity extends TimestampedEntity {
	
	@Id
	@TimeOrderedId
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;
	
}
