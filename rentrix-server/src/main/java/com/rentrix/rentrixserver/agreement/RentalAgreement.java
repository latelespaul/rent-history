package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One uploaded rental agreement and what its uploader typed from it (§5.20, D-123 – D-131). The typed fields are
 * stored masked (D-131); the file is stored encrypted in PRIVATE storage under {@code storage_key}, which is null once
 * retention deleted it ({@code file_deleted_at}). The fields, the hash, the anchor number and the tier are kept.
 * Columns of the later agreement checks (D-125, D-128 – D-130) exist in the table and are mapped when those land.
 */
@Entity
@Table(name = "rental_agreements")
@Getter
@Setter
public class RentalAgreement extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploader_id", nullable = false)
	private User uploader;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "uploader_side", nullable = false, length = 16)
	private TenancyParty uploaderSide;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tenancy_id")
	private Tenancy tenancy;
	
	@Column(name = "city_code", nullable = false, length = 8)
	private String cityCode;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "anchor_kind", nullable = false, length = 24)
	private AnchorKind anchorKind;
	
	@Column(name = "anchor_number", length = 64)
	private String anchorNumber;
	
	/** Upper case without spaces and dashes, for reuse checks (D-129). */
	@Column(name = "anchor_number_key", length = 64)
	private String anchorNumberKey;
	
	@Column(name = "anchor_date")
	private LocalDate anchorDate;
	
	@Column(name = "landlord_name", length = 120)
	private String landlordName;
	
	@Column(name = "tenant_name", length = 120)
	private String tenantName;
	
	@Column(name = "address_text", length = 500)
	private String addressText;
	
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "pincode", length = 6)
	private String pincode;
	
	@Column(name = "monthly_rent_inr")
	private Long monthlyRentInr;
	
	@Column(name = "deposit_inr")
	private Long depositInr;
	
	@Column(name = "start_date")
	private LocalDate startDate;
	
	@Column(name = "end_date")
	private LocalDate endDate;
	
	@Column(name = "extractor", nullable = false, length = 16)
	private String extractor;
	
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "sha256", nullable = false, length = 64)
	private String sha256;
	
	@Column(name = "content_type", nullable = false, length = 64)
	private String contentType;
	
	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;
	
	/** The encrypted file in PRIVATE storage; null once it is deleted (D-131). */
	@Column(name = "storage_key")
	private String storageKey;
	
	@Column(name = "uploader_confirmed_at")
	private Instant uploaderConfirmedAt;
	
	/** The other party's confirmation of the same file (D-128); not built yet, read by the retention rule. */
	@Column(name = "counterparty_confirmed_at")
	private Instant counterpartyConfirmedAt;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "tier", nullable = false, length = 16)
	private AgreementTier tier = AgreementTier.UNVERIFIED;
	
	/** Comma list of {@link AgreementFlag} names, for admins. */
	@Column(name = "flags", length = 200)
	private String flags;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private AgreementStatus status = AgreementStatus.DRAFT;
	
	@Column(name = "consent_at", nullable = false)
	private Instant consentAt;
	
	@Column(name = "decided_at")
	private Instant decidedAt;
	
	/** When retention deletes the file (D-131); recomputed whenever the decision or a confirmation is recorded. */
	@Column(name = "purge_after")
	private Instant purgeAfter;
	
	@Column(name = "file_deleted_at")
	private Instant fileDeletedAt;
	
	public Set<AgreementFlag> flagSet() {
		if (flags == null || flags.isBlank()) {
			return EnumSet.noneOf(AgreementFlag.class);
		}
		return Arrays.stream(flags.split(",")).map(String::trim).filter(s -> !s.isEmpty())
			.map(AgreementFlag::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(AgreementFlag.class)));
	}
	
	public void addFlags(Set<AgreementFlag> more) {
		Set<AgreementFlag> all = flagSet();
		all.addAll(more);
		flags = all.isEmpty() ? null : all.stream().map(Enum::name).collect(Collectors.joining(","));
	}
	
	public boolean fileAvailable() {
		return storageKey != null;
	}
	
}
