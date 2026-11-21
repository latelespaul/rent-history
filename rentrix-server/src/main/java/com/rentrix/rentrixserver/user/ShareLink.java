package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A revocable link that shows a tenant's profile and reputation (D-54). Only the hash is stored. */
@Entity
@Table(name = "share_links")
@Getter
@Setter
public class ShareLink extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	
	/** SHA-256 hex of the token. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;
	
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	
	@Column(name = "revoked_at")
	private Instant revokedAt;
	
}
