package com.rentrix.rentrixserver.identity;

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

/**
 * One identity check per user per method (§5.15, D-106). Only masked data: {@code last4} and, for PAN and phone,
 * {@code valueHmac} for duplicate detection. A full PAN, any Aadhaar digit beyond the last 4 and an OTP code are
 * never stored. Serves both roles: a user who is tenant and landlord verifies once.
 */
@Entity
@Table(name = "identity_verifications")
@Getter
@Setter
public class IdentityVerification extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "method", nullable = false, updatable = false, length = 16)
	private IdentityMethod method;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private IdentityStatus status;
	
	/** PAN: last 4 characters; Aadhaar: last 4 printed digits; phone: last 4 digits; null for LinkedIn. */
	@Column(name = "last4", length = 4)
	private String last4;
	
	/** HMAC-SHA256 hex of the normalised PAN or E.164 phone ({@code ID_HASH_KEY}); null for Aadhaar and LinkedIn. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "value_hmac", length = 64)
	private String valueHmac;
	
	/** LinkedIn only: normalised profile URL, private to the owner and admins (D-93). */
	@Column(name = "linkedin_url", length = 200)
	private String linkedinUrl;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "checked_by", length = 16)
	private CheckedBy checkedBy;
	
	/** Null in v1; the KYC provider once one is used (D-104). */
	@Column(name = "provider", length = 40)
	private String provider;
	
	@Column(name = "provider_ref", length = 100)
	private String providerRef;
	
	@Column(name = "verified_at")
	private Instant verifiedAt;
	
	/** The admin who verified it; null for OTP. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "verified_by")
	private User verifiedBy;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "reject_code", length = 32)
	private IdentityRejectCode rejectCode;
	
	@Column(name = "reject_note", length = 1000)
	private String rejectNote;
	
	@Column(name = "revoked_at")
	private Instant revokedAt;
	
	/** Clears the previous decision before a new attempt. */
	void resetDecision() {
		checkedBy = null;
		verifiedAt = null;
		verifiedBy = null;
		rejectCode = null;
		rejectNote = null;
		revokedAt = null;
	}
	
}
