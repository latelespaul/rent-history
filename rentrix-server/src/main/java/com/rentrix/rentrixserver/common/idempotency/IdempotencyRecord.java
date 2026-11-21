package com.rentrix.rentrixserver.common.idempotency;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** One stored {@code Idempotency-Key} for one caller (V2 migration). */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord extends BaseEntity {
	
	/** Who sent the key (the caller's user public_id), so keys never collide across users. */
	@Column(name = "scope", nullable = false, length = 64)
	private String scope;
	
	@Column(name = "idempotency_key", nullable = false, length = 64)
	private String idempotencyKey;
	
	/** SHA-256 (hex) of method, path and body: the same key with another request is refused. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "state", nullable = false, length = 16)
	private IdempotencyState state;
	
	@Column(name = "response_status")
	private Integer responseStatus;
	
	@Column(name = "response_content_type")
	private String responseContentType;
	
	@Column(name = "response_location", length = 2048)
	private String responseLocation;
	
	@Column(name = "response_body", columnDefinition = "TEXT")
	private String responseBody;
	
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	
	IdempotencyRecord(String scope, String idempotencyKey, String requestHash, Instant expiresAt) {
		this.scope = scope;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.state = IdempotencyState.IN_PROGRESS;
		this.expiresAt = expiresAt;
	}
	
}
