package com.rentrix.rentrixserver.tenancy;

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

/** Optional private evidence for a tenancy (§5.8); never public (D-30). */
@Entity
@Table(name = "tenancy_documents")
@Getter
@Setter
public class TenancyDocument extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenancy_id", nullable = false)
	private Tenancy tenancy;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploaded_by", nullable = false)
	private User uploadedBy;
	
	@Column(name = "storage_key", nullable = false)
	private String storageKey;
	
	@Column(name = "content_type", nullable = false, length = 64)
	private String contentType;
	
	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;
	
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "sha256", nullable = false, length = 64)
	private String sha256;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private DocumentStatus status = DocumentStatus.UPLOADED;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by")
	private User reviewedBy;
	
	@Column(name = "reviewed_at")
	private Instant reviewedAt;
	
}
