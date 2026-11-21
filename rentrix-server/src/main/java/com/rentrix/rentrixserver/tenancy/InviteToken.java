package com.rentrix.rentrixserver.tenancy;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
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

/** Single-use tenancy invite for someone without an account (§5.12): hashed, 14-day expiry. */
@Entity
@Table(name = "invite_tokens")
@Getter
@Setter
public class InviteToken extends BaseEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenancy_id", nullable = false)
	private Tenancy tenancy;
	
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;
	
	@Column(name = "email", nullable = false, length = 320)
	private String email;
	
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	
	@Column(name = "used_at")
	private Instant usedAt;
	
}
