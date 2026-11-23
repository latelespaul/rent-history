package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.admin.AdminConsoleMembers;
import com.rentrix.rentrixserver.admin.AuditLog;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.admin.ModerationTargetType;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.identity.IdentityRejectCode;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.identity.IdentityStatus;
import com.rentrix.rentrixserver.identity.IdentityVerification;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Admin decisions on applications (D-87 – D-90, D-105): approve ({@code PENDING → APPROVED}), reject
 * ({@code PENDING → REJECTED}) and revoke ({@code APPROVED → REJECTED}). Each writes one {@code moderation_actions}
 * row and one {@link VerificationEvent} in the same transaction, refuses an admin deciding their own application
 * ({@code 403 admin_conflict}, D-88), and emails the user after commit. The caller's {@code ADMIN} role is read from
 * the database (D-19), so a revoked admin is refused at once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationDecisions {
	
	/** One identity result inside an approve call (D-105). */
	public record IdentityResult(UUID id, IdentityStatus result, IdentityRejectCode code, String note) {
	}
	
	private final VerificationApplicationRepository applications;
	private final VerificationService verification;
	private final IdentityService identity;
	private final UserRepository users;
	private final AdminConsoleMembers adminMembers;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	
	/**
	 * Applies the identity results first (each audited), then needs any one {@code VERIFIED} posting method of the
	 * applicant: phone, masked Aadhaar or LinkedIn ({@code 422 no_verified_method}, D-144); on that refusal nothing is
	 * kept, the identity results included.
	 */
	@Transactional
	public AdminApplicationView approve(UUID adminId, UUID applicationId, String note, List<IdentityResult> results) {
		User admin = admin(adminId);
		VerificationApplication application = decidable(admin, applicationId, VerificationStatus.PENDING);
		User owner = application.getUser();
		Instant now = now();
		for (IdentityResult result : results == null ? List.<IdentityResult>of() : results) {
			IdentityVerification check = identity.decideByAdmin(admin, owner, result.id(), result.result(),
				result.code(), blankToNull(result.note()));
			verification.event(application, VerificationEventType.IDENTITY_CHECKED, admin,
				check.getMethod().name() + ":" + check.getStatus().name(), blankToNull(result.note()), now);
		}
		if (!identity.hasPostingMethod(owner)) {
			throw ApiException.rule("no_verified_method",
				"The applicant has no verified phone, Aadhaar or LinkedIn yet, so the application cannot be approved.");
		}
		String cleanNote = blankToNull(note);
		application.setStatus(VerificationStatus.APPROVED);
		application.setDecidedAt(now);
		application.setDecidedBy(admin);
		application.setDecisionCode(null);
		application.setDecisionNote(cleanNote);
		return decided(admin, application, VerificationEventType.APPROVED, ModerationActionType.APPROVE_APPLICATION,
			null, cleanNote, now);
	}
	
	@Transactional
	public AdminApplicationView reject(UUID adminId, UUID applicationId, VerificationDecisionCode code, String note) {
		User admin = admin(adminId);
		VerificationApplication application = decidable(admin, applicationId, VerificationStatus.PENDING);
		return refuse(admin, application, VerificationEventType.REJECTED, ModerationActionType.REJECT_APPLICATION, code,
			note);
	}
	
	@Transactional
	public AdminApplicationView revoke(UUID adminId, UUID applicationId, VerificationDecisionCode code, String note) {
		User admin = admin(adminId);
		VerificationApplication application = decidable(admin, applicationId, VerificationStatus.APPROVED);
		return refuse(admin, application, VerificationEventType.REVOKED, ModerationActionType.REVOKE_APPROVAL, code,
			note);
	}
	
	private AdminApplicationView refuse(User admin, VerificationApplication application, VerificationEventType type,
		ModerationActionType action, VerificationDecisionCode code, String note) {
		Instant now = now();
		String cleanNote = note.trim();
		application.setStatus(VerificationStatus.REJECTED);
		application.setDecidedAt(now);
		application.setDecidedBy(admin);
		application.setDecisionCode(code);
		application.setDecisionNote(cleanNote);
		return decided(admin, application, type, action, code, cleanNote, now);
	}
	
	private AdminApplicationView decided(User admin, VerificationApplication application, VerificationEventType type,
		ModerationActionType action, VerificationDecisionCode code, String note, Instant now) {
		verification.save(application);
		verification.event(application, type, admin, code == null ? null : code.name(), note, now);
		audit.record(admin, action, ModerationTargetType.APPLICATION, application.getId(),
			application.getPublicId().toString(), code == null ? note : code.name() + ": " + note);
		User owner = application.getUser();
		events.publishEvent(new VerificationDecided(owner.getEmail(), owner.getDisplayName(), application.getRole(),
			type, code, type == VerificationEventType.APPROVED ? null : note));
		log.info("Verification {}: application {} ({}) by admin {}", type, application.getPublicId(),
			application.getRole(), admin.getPublicId());
		return AdminApplicationView.of(application);
	}
	
	private VerificationApplication decidable(User admin, UUID applicationId, VerificationStatus from) {
		VerificationApplication application = applications.findByPublicId(applicationId)
			.orElseThrow(() -> ApiException.notFound("Application"));
		if (application.getUser().getId().equals(admin.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "admin_conflict",
				"You cannot decide your own application. Another admin must do it.");
		}
		if (application.getStatus() != from) {
			throw ApiException.conflict("invalid_transition", "This application is " + application.getStatus()
				+ "; only a " + from + " application can be decided this way.");
		}
		return application;
	}
	
	/** The caller must be an admin console member now ({@code ADMIN_CONSOLE_EMAILS}, or {@code user_roles} under dev, D-19), not only in the token. */
	private User admin(UUID adminId) {
		return users.findByPublicId(adminId)
			.filter(adminMembers::isMember)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "forbidden", "You are not allowed to do this."));
	}
	
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}
	
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
	
}
