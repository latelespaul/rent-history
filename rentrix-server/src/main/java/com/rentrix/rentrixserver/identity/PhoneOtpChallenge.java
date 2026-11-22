package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.user.User;
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

/**
 * One SMS code sent for phone verification (§5.16, D-101). The code is kept only as an HMAC; {@code publicId}
 * is the {@code challengeId} the client sends back.
 */
@Entity
@Table(name = "phone_otp_challenges")
@Getter
@Setter
public class PhoneOtpChallenge extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;
	
	/** The number being verified, E.164; private (D-57). */
	@Column(name = "phone_e164", nullable = false, updatable = false, length = 16)
	private String phoneE164;
	
	/** HMAC of the phone, for the per-number send limit across accounts. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "phone_hmac", nullable = false, updatable = false, length = 64)
	private String phoneHmac;
	
	/** HMAC of the 6-digit code; the code itself is never stored. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "code_hmac", nullable = false, updatable = false, length = 64)
	private String codeHmac;
	
	@Column(name = "attempts", nullable = false)
	private int attempts;
	
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	
	@Column(name = "consumed_at")
	private Instant consumedAt;
	
}
