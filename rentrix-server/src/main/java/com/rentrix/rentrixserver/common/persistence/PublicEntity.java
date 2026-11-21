package com.rentrix.rentrixserver.common.persistence;

import com.rentrix.rentrixserver.common.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Tables whose rows are addressed by the API through {@code public_id} (UUIDv7, D-12). */
@MappedSuperclass
@Getter
public abstract class PublicEntity extends BaseEntity {
	
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
	private UUID publicId = UuidV7.next();
	
}
