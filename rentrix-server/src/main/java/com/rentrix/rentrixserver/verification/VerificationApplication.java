package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.user.Role;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A user's verification for one role (§5.13, D-89): it carries the approval that opens trust writes for that role
 * (D-91). {@code DRAFT → PENDING → APPROVED / REJECTED}; a rejected application is resubmitted with
 * {@code revision + 1}, and an approval can be revoked back to {@code REJECTED} (D-90). Previous revisions live in
 * {@link VerificationEvent}s.
 */
@Entity
@Table(name = "verification_applications")
@Getter
@Setter
public class VerificationApplication extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "role", nullable = false, updatable = false, length = 16)
	private Role role;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private VerificationStatus status = VerificationStatus.DRAFT;
	
	@Column(name = "revision", nullable = false)
	private int revision = 1;
	
	/** Comma list set by the API, e.g. {@code DUPLICATE_ID}, {@code DUPLICATE_PHONE}; shown to admins only. */
	@Column(name = "flags", length = 200)
	private String flags;
	
	@Column(name = "submitted_at")
	private Instant submittedAt;
	
	@Column(name = "decided_at")
	private Instant decidedAt;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by")
	private User decidedBy;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "decision_code", length = 32)
	private VerificationDecisionCode decisionCode;
	
	/** On reject or revoke: shown to the user. On approve: an optional note for admins only. */
	@Column(name = "decision_note", length = 1000)
	private String decisionNote;
	
	public Set<String> flagSet() {
		if (flags == null || flags.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(flags.split(",")).map(String::trim).filter(f -> !f.isEmpty())
			.collect(Collectors.toCollection(TreeSet::new));
	}
	
	public void setFlagSet(Collection<String> values) {
		flags = values == null || values.isEmpty() ? null : String.join(",", new TreeSet<>(values));
	}
	
	void clearDecision() {
		decidedAt = null;
		decidedBy = null;
		decisionCode = null;
		decisionNote = null;
	}
	
}
