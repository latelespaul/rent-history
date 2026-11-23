package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.identity.IdentityMethod;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.identity.IdentityVerification;
import com.rentrix.rentrixserver.identity.IdentityVerificationRepository;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The user's side of onboarding approvals (§7.3, D-89 – D-91): the status per held role ({@code GET /me}), the
 * step overview ({@code GET /me/verification}) and submit ({@code DRAFT} or {@code REJECTED} → {@code PENDING}).
 * Applications are created on the first submit; until then a held role reports {@code DRAFT}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService implements VerificationStatuses {
	
	/** Submissions allowed per application in {@link #SUBMIT_WINDOW} (D-90, like D-45). */
	static final int SUBMITS_PER_WINDOW = 3;
	static final Duration SUBMIT_WINDOW = Duration.ofHours(24);
	
	/** The roles that need an approval; {@code ADMIN} never does (D-19). */
	static final List<Role> APPROVAL_ROLES = List.of(Role.TENANT, Role.LANDLORD);
	
	private final VerificationApplicationRepository applications;
	private final VerificationEventRepository events;
	private final UserRepository users;
	private final IdentityService identity;
	private final IdentityVerificationRepository identityChecks;
	private final Clock clock;
	
	/** {@code GET /me} {@code verification}: the status per held {@code TENANT} / {@code LANDLORD} role, from the DB. */
	@Override
	@Transactional(readOnly = true)
	public Map<Role, VerificationStatus> of(User user) {
		Map<Role, VerificationStatus> statuses = new EnumMap<>(Role.class);
		for (Role role : APPROVAL_ROLES) {
			if (user.hasRole(role)) {
				statuses.put(role, VerificationStatus.DRAFT);
			}
		}
		if (user.getId() != null && !statuses.isEmpty()) {
			for (VerificationApplication application : applications.findByUser(user)) {
				statuses.computeIfPresent(application.getRole(), (role, draft) -> application.getStatus());
			}
		}
		return statuses;
	}
	
	/** The status of one role, read from the DB on each call (the gate, D-91). */
	@Transactional(readOnly = true)
	public VerificationStatus status(User user, Role role) {
		return applications.findByUserAndRole(user, role)
			.map(VerificationApplication::getStatus)
			.orElse(VerificationStatus.DRAFT);
	}
	
	@Transactional(readOnly = true)
	public List<VerificationView> view(UUID callerId) {
		User user = load(callerId);
		Steps steps = steps(user);
		return APPROVAL_ROLES.stream()
			.filter(user::hasRole)
			.map(role -> view(user, role, applications.findByUserAndRole(user, role), steps))
			.toList();
	}
	
	/**
	 * {@code POST /me/verification/{role}/submit} ("Request verification", D-133): {@code DRAFT} or {@code REJECTED}
	 * → {@code PENDING}, a rejected application with {@code revision + 1}. Needs the profile and a {@code VERIFIED}
	 * phone ({@code 422 verification_incomplete} listing {@code profile} / {@code phone}, D-117); PAN, Aadhaar,
	 * LinkedIn and the rental details are optional. 3 submits per 24 h ({@code 429}, D-90).
	 */
	@Transactional
	public VerificationView submit(UUID callerId, String rolePath) {
		User user = load(callerId);
		Role role = heldRole(user, rolePath);
		VerificationApplication application = applications.findByUserAndRole(user, role).orElseGet(() -> {
			VerificationApplication created = new VerificationApplication();
			created.setUser(user);
			created.setRole(role);
			return created;
		});
		switch (application.getStatus()) {
			case PENDING -> throw ApiException.conflict("invalid_transition",
				"This application is already waiting for an admin.");
			case APPROVED -> throw ApiException.conflict("invalid_transition", "This application is already approved.");
			case DRAFT, REJECTED -> {
				// may be submitted
			}
		}
		Steps steps = steps(user);
		List<String> missing = new ArrayList<>();
		if (steps.profile() == StepStatus.MISSING) {
			missing.add("profile");
		}
		if (steps.method() == StepStatus.MISSING) {
			missing.add("method");
		}
		if (!missing.isEmpty()) {
			throw ApiException.verificationIncomplete(missing);
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		if (application.getId() != null) {
			List<VerificationEvent> recent = events.findByApplicationAndEventAndCreatedAtAfterOrderByCreatedAtAsc(
				application, VerificationEventType.SUBMITTED, now.minus(SUBMIT_WINDOW));
			if (recent.size() >= SUBMITS_PER_WINDOW) {
				Instant oldest = recent.get(recent.size() - SUBMITS_PER_WINDOW).getCreatedAt();
				throw ApiException.rateLimited(Duration.between(now, oldest.plus(SUBMIT_WINDOW)));
			}
		}
		if (application.getStatus() == VerificationStatus.REJECTED) {
			application.setRevision(application.getRevision() + 1);
		}
		application.setStatus(VerificationStatus.PENDING);
		application.setSubmittedAt(now);
		application.clearDecision();
		application.setFlagSet(identity.flags(user));
		save(application);
		event(application, VerificationEventType.SUBMITTED, user, null, null, now);
		log.info("Verification submitted: user {} role {} revision {}", user.getPublicId(), role,
			application.getRevision());
		return view(user, role, Optional.of(application), steps);
	}
	
	/**
	 * Writes the application now, so a concurrent decision or submit that changed it first (the {@code version}
	 * check) or created it first (unique user + role) is {@code 409 invalid_transition} and nothing else is kept.
	 */
	void save(VerificationApplication application) {
		boolean created = application.getId() == null;
		try {
			applications.saveAndFlush(application);
		} catch (OptimisticLockingFailureException e) {
			throw changedConcurrently();
		} catch (DataIntegrityViolationException e) {
			if (!created) {
				throw e;
			}
			throw changedConcurrently();
		}
	}
	
	private static ApiException changedConcurrently() {
		return ApiException.conflict("invalid_transition",
			"This application was changed by another request at the same time. Reload it and try again.");
	}
	
	VerificationEvent event(VerificationApplication application, VerificationEventType type, User actor, String code,
		String note, Instant at) {
		VerificationEvent event = new VerificationEvent();
		event.setApplication(application);
		event.setRevision(application.getRevision());
		event.setEvent(type);
		event.setActor(actor);
		event.setCode(code);
		event.setNote(note);
		event.setCreatedAt(at);
		return events.save(event);
	}
	
	private Steps steps(User user) {
		StepStatus profile = user.getDisplayName() != null && !user.getDisplayName().isBlank()
			&& user.getHomeCity() != null ? StepStatus.DONE : StepStatus.MISSING;
		StepStatus phone = identity.isPhoneVerified(user) ? StepStatus.DONE : StepStatus.MISSING;
		// The posting rule (D-144): any one of phone, masked Aadhaar or LinkedIn verified.
		StepStatus method = identity.hasPostingMethod(user) ? StepStatus.DONE
			: identity.hasPendingPostingMethod(user) ? StepStatus.PENDING : StepStatus.MISSING;
		// The optional extra ID (D-120): PAN or masked Aadhaar; skipped when the user has added neither.
		StepStatus id = identity.isIdVerified(user) ? StepStatus.DONE
			: identity.hasPendingIdMethod(user) ? StepStatus.PENDING
			: identity.hasRejectedIdMethod(user) ? StepStatus.REJECTED : StepStatus.SKIPPED;
		StepStatus linkedin = identityChecks.findByUserAndMethod(user, IdentityMethod.LINKEDIN)
			.map(IdentityVerification::getStatus)
			.map(status -> switch (status) {
				case VERIFIED -> StepStatus.DONE;
				case PENDING -> StepStatus.PENDING;
				case REJECTED -> StepStatus.REJECTED;
				case REVOKED -> StepStatus.SKIPPED;
			})
			.orElse(StepStatus.SKIPPED);
		return new Steps(profile, phone, method, id, linkedin);
	}
	
	private static VerificationView view(User user, Role role, Optional<VerificationApplication> found, Steps steps) {
		// Rental details are an optional step for both roles (D-120); the agreement upload comes with PLAN B18.
		VerificationView.Steps roleSteps = new VerificationView.Steps(steps.profile(), steps.phone(), steps.method(),
			steps.identity(), steps.linkedin(), StepStatus.SKIPPED);
		if (found.isEmpty()) {
			return new VerificationView(null, role, VerificationStatus.DRAFT, 1, roleSteps, null, null);
		}
		VerificationApplication application = found.get();
		VerificationView.Decision decision = switch (application.getStatus()) {
			case REJECTED -> new VerificationView.Decision(application.getDecisionCode(), application.getDecisionNote(),
				application.getDecidedAt());
			case APPROVED -> new VerificationView.Decision(null, null, application.getDecidedAt());
			case DRAFT, PENDING -> null;
		};
		return new VerificationView(application.getPublicId(), role, application.getStatus(),
			application.getRevision(), roleSteps, application.getSubmittedAt(), decision);
	}
	
	private User load(UUID publicId) {
		return users.findByPublicId(publicId)
			.filter(u -> u.getStatus() != UserStatus.DELETED)
			.orElseThrow(() -> ApiException.notFound("User"));
	}
	
	/** {@code tenant} / {@code LANDLORD} in the path; a role the user does not hold (or ADMIN) is {@code 404}. */
	private static Role heldRole(User user, String rolePath) {
		Role role;
		try {
			role = Role.valueOf(rolePath.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw ApiException.notFound("Verification");
		}
		if (!APPROVAL_ROLES.contains(role) || !user.hasRole(role)) {
			throw ApiException.notFound("Verification");
		}
		return role;
	}
	
	private record Steps(StepStatus profile, StepStatus phone, StepStatus method, StepStatus identity,
		StepStatus linkedin) {
	}
	
}
