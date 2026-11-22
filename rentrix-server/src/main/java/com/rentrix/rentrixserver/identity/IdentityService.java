package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.admin.AuditLog;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.admin.ModerationTargetType;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.notification.sms.PhoneMasks;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Identity checks per user (§5.15, D-99 – D-106, D-117). The two rules the gate and the badge use live here: a user
 * may post when their {@code PHONE_OTP} is {@code VERIFIED} ({@link #isPhoneVerified}, D-117, D-132), and is
 * <b>ID verified</b> (the public badge) when {@code AADHAAR} or {@code PAN} is {@code VERIFIED}
 * ({@link #isIdVerified}, D-100). LinkedIn is a bonus badge and never counts. The same rules hold for tenants and
 * landlords.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {
	
	/**
	 * The methods behind the "ID verified" badge (D-100, changed in design v4): the optional extra ID. A verified
	 * phone alone gives no badge, because every user who can post has one (D-117).
	 */
	public static final Set<IdentityMethod> ID_METHODS = EnumSet.of(IdentityMethod.AADHAAR, IdentityMethod.PAN);
	
	/** The ID methods an admin decides from an upload, also inside an application decision (D-102, D-103, D-105). */
	public static final Set<IdentityMethod> ADMIN_CHECKED_ID_METHODS =
		EnumSet.of(IdentityMethod.AADHAAR, IdentityMethod.PAN);
	
	/** Reject codes an admin can give a PAN or Aadhaar check (§5.15). */
	static final Set<IdentityRejectCode> ADMIN_REJECT_CODES = EnumSet.of(IdentityRejectCode.ID_UNREADABLE,
		IdentityRejectCode.ID_NAME_MISMATCH, IdentityRejectCode.AADHAAR_NOT_MASKED, IdentityRejectCode.OTHER);
	
	/** Admin-only flag: the user's verified phone is also verified on another active account (D-101). */
	public static final String DUPLICATE_PHONE = "DUPLICATE_PHONE";
	
	private final IdentityVerificationRepository checks;
	private final PhoneOtpAvailability phoneOtp;
	private final AdminReviewProvider adminReview;
	private final AuditLog audit;
	private final Clock clock;
	
	/** The badge rule (D-100): PAN or masked Aadhaar is verified. */
	@Transactional(readOnly = true)
	public boolean isIdVerified(User user) {
		return checks.existsByUserAndMethodInAndStatus(user, ID_METHODS, IdentityStatus.VERIFIED);
	}
	
	/** The posting rule (D-117, D-132): the phone is verified by OTP and not revoked. */
	@Transactional(readOnly = true)
	public boolean isPhoneVerified(User user) {
		return user.getId() != null && checks.existsByUserAndMethodInAndStatus(user, EnumSet.of(IdentityMethod.PHONE_OTP),
			IdentityStatus.VERIFIED);
	}
	
	/** PAN or masked Aadhaar waits for an admin check (the optional ID step shows it as pending, D-120). */
	@Transactional(readOnly = true)
	public boolean hasPendingIdMethod(User user) {
		return checks.existsByUserAndMethodInAndStatus(user, ID_METHODS, IdentityStatus.PENDING);
	}
	
	/** PAN or masked Aadhaar was rejected (the optional ID step shows the reason, D-120). */
	@Transactional(readOnly = true)
	public boolean hasRejectedIdMethod(User user) {
		return checks.existsByUserAndMethodInAndStatus(user, ID_METHODS, IdentityStatus.REJECTED);
	}
	
	/** The public badges (D-100). */
	@Transactional(readOnly = true)
	public Badges badges(User user) {
		if (user.getId() == null) {
			return Badges.NONE;
		}
		return badgesOf(checks.findByUser(user));
	}
	
	/** The caller's own checks, masked only (D-106). */
	@Transactional(readOnly = true)
	public IdentityView view(User user) {
		Map<IdentityMethod, IdentityVerification> byMethod = new EnumMap<>(IdentityMethod.class);
		List<IdentityVerification> rows = checks.findByUser(user);
		rows.forEach(row -> byMethod.put(row.getMethod(), row));
		Badges badges = badgesOf(rows);
		List<IdentityView.Method> methods = Arrays.stream(IdentityMethod.values())
			.map(method -> method(method, byMethod.get(method)))
			.toList();
		return new IdentityView(badges.idVerified(), badges.linkedinVerified(), methods);
	}
	
	/**
	 * Admin-only flags for the user's applications (D-101); PLAN B11 copies them onto
	 * {@code verification_applications.flags}. {@code DUPLICATE_ID} for PAN comes with B12.
	 */
	@Transactional(readOnly = true)
	public Set<String> flags(User user) {
		Set<String> flags = new TreeSet<>();
		checks.findByUserAndMethod(user, IdentityMethod.PHONE_OTP)
			.filter(phone -> phone.getStatus() == IdentityStatus.VERIFIED && phone.getValueHmac() != null)
			.filter(phone -> checks.countVerifiedOnOtherAccounts(IdentityMethod.PHONE_OTP, phone.getValueHmac(), user,
				UserStatus.DELETED) > 0)
			.ifPresent(phone -> flags.add(DUPLICATE_PHONE));
		return flags;
	}
	
	/**
	 * {@code PATCH /me} changed or cleared the phone: the verified phone no longer belongs to the account, so its
	 * check is {@code REVOKED} with {@code PHONE_CHANGED} (D-101). Call before {@code users.phone} is updated.
	 */
	@Transactional
	public void phoneChanged(User user) {
		checks.findByUserAndMethod(user, IdentityMethod.PHONE_OTP)
			.filter(phone -> phone.getStatus() == IdentityStatus.VERIFIED)
			.ifPresent(phone -> {
				phone.setStatus(IdentityStatus.REVOKED);
				phone.setRejectCode(IdentityRejectCode.PHONE_CHANGED);
				phone.setRejectNote("The phone number on your account changed. Verify the new number.");
				phone.setRevokedAt(clock.instant().truncatedTo(ChronoUnit.MILLIS));
				checks.save(phone);
				log.info("Phone check revoked for user {}: PHONE_CHANGED", user.getPublicId());
			});
	}
	
	/**
	 * An admin decides one {@code PENDING} PAN or Aadhaar check of {@code owner} while deciding their application
	 * (D-105), audited as {@code VERIFY_IDENTITY} / {@code REJECT_IDENTITY} (D-87) in the caller's transaction.
	 * A reject needs a code. The ID file is deleted on the decision once files exist (PLAN B12, D-96).
	 *
	 * @param checkId the check's {@code public_id}; a check of another user is {@code 404}
	 * @param result  {@code VERIFIED} or {@code REJECTED}
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public IdentityVerification decideByAdmin(User admin, User owner, UUID checkId, IdentityStatus result,
		IdentityRejectCode code, String note) {
		IdentityVerification row = checks.findByPublicId(checkId)
			.filter(check -> check.getUser().getId().equals(owner.getId()))
			.orElseThrow(() -> ApiException.notFound("Identity check"));
		if (!ADMIN_CHECKED_ID_METHODS.contains(row.getMethod())) {
			throw ApiException.rule("identity_not_decidable",
				"Only PAN and masked Aadhaar checks are decided with an application.");
		}
		if (row.getStatus() != IdentityStatus.PENDING) {
			throw ApiException.conflict("invalid_transition", "This identity check is not waiting for a decision.");
		}
		if (result != IdentityStatus.VERIFIED && result != IdentityStatus.REJECTED) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("identity.result", "invalid",
				"Use VERIFIED or REJECTED.")));
		}
		if (result == IdentityStatus.REJECTED && (code == null || !ADMIN_REJECT_CODES.contains(code))) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("identity.code", "invalid",
				"Give a reason: ID_UNREADABLE, ID_NAME_MISMATCH, AADHAAR_NOT_MASKED or OTHER.")));
		}
		row.resetDecision();
		row.setStatus(result);
		row.setCheckedBy(CheckedBy.ADMIN);
		if (result == IdentityStatus.VERIFIED) {
			row.setVerifiedAt(clock.instant().truncatedTo(ChronoUnit.MILLIS));
			row.setVerifiedBy(admin);
		} else {
			row.setRejectCode(code);
			row.setRejectNote(note);
		}
		checks.save(row);
		audit.record(admin, result == IdentityStatus.VERIFIED ? ModerationActionType.VERIFY_IDENTITY
				: ModerationActionType.REJECT_IDENTITY, ModerationTargetType.IDENTITY, row.getId(),
			row.getPublicId().toString(), reason(code, note));
		return row;
	}
	
	private static String reason(IdentityRejectCode code, String note) {
		if (code == null) {
			return note;
		}
		return note == null || note.isBlank() ? code.name() : code.name() + ": " + note;
	}
	
	/**
	 * {@code PUT /me/identity/linkedin}: stores the normalised URL (never fetched, D-93) and queues it for an admin.
	 * Sending the URL that is already verified changes nothing.
	 */
	@Transactional
	public IdentityView setLinkedin(User user, String normalisedUrl) {
		IdentityVerification row = checks.findByUserAndMethod(user, IdentityMethod.LINKEDIN).orElseGet(() -> {
			IdentityVerification created = new IdentityVerification();
			created.setUser(user);
			created.setMethod(IdentityMethod.LINKEDIN);
			return created;
		});
		boolean unchanged = normalisedUrl.equals(row.getLinkedinUrl())
			&& (row.getStatus() == IdentityStatus.VERIFIED || row.getStatus() == IdentityStatus.PENDING);
		if (!unchanged) {
			row.setLinkedinUrl(normalisedUrl);
			adminReview.start(row);
		}
		return view(user);
	}
	
	/** {@code DELETE /me/identity/linkedin}: LinkedIn is optional, so the row (and its badge) simply goes. */
	@Transactional
	public void removeLinkedin(User user) {
		checks.findByUserAndMethod(user, IdentityMethod.LINKEDIN).ifPresent(checks::delete);
	}
	
	private static Badges badgesOf(List<IdentityVerification> rows) {
		boolean id = rows.stream()
			.anyMatch(r -> r.getMethod().isIdMethod() && r.getStatus() == IdentityStatus.VERIFIED);
		boolean linkedin = rows.stream()
			.anyMatch(r -> r.getMethod() == IdentityMethod.LINKEDIN && r.getStatus() == IdentityStatus.VERIFIED);
		return new Badges(id, linkedin);
	}
	
	private IdentityView.Method method(IdentityMethod method, IdentityVerification row) {
		boolean available = switch (method) {
			case PHONE_OTP -> phoneOtp.available();
			case LINKEDIN -> true;
			// PAN and masked Aadhaar need the private document upload of PLAN B12.
			case PAN, AADHAAR -> false;
		};
		if (row == null) {
			return new IdentityView.Method(method, null, null, null, null, null, available);
		}
		IdentityView.Reject reject = row.getRejectCode() == null ? null
			: new IdentityView.Reject(row.getRejectCode(), row.getRejectNote());
		return new IdentityView.Method(method, row.getStatus(), masked(row), row.getCheckedBy(), row.getVerifiedAt(),
			reject, available);
	}
	
	/** D-106 masks. */
	static String masked(IdentityVerification row) {
		if (row.getMethod() == IdentityMethod.LINKEDIN) {
			return row.getLinkedinUrl();
		}
		String last4 = row.getLast4();
		if (last4 == null) {
			return null;
		}
		return switch (row.getMethod()) {
			case PAN -> "XXXXXX" + last4;
			case AADHAAR -> "XXXX XXXX " + last4;
			case PHONE_OTP -> PhoneMasks.fromLast4(last4);
			case LINKEDIN -> throw new IllegalStateException("unreachable");
		};
	}
	
}
