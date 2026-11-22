package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.notification.sms.PhoneMasks;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

/**
 * Identity checks per user (§5.15, D-99 – D-106). The rule every gate and badge uses lives here: a user is
 * <b>ID verified</b> when at least one of {@code AADHAAR}, {@code PAN} or {@code PHONE_OTP} is {@code VERIFIED}
 * (D-99). LinkedIn is a bonus badge and never counts. The same rule holds for tenants and landlords.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {
	
	/** The methods that make a user ID verified (D-99). */
	public static final Set<IdentityMethod> ID_METHODS =
		EnumSet.of(IdentityMethod.AADHAAR, IdentityMethod.PAN, IdentityMethod.PHONE_OTP);
	
	/** Admin-only flag: the user's verified phone is also verified on another active account (D-101). */
	public static final String DUPLICATE_PHONE = "DUPLICATE_PHONE";
	
	private final IdentityVerificationRepository checks;
	private final PhoneOtpAvailability phoneOtp;
	private final AdminReviewProvider adminReview;
	private final Clock clock;
	
	/** D-99: any one of Aadhaar, PAN or phone OTP is verified. */
	@Transactional(readOnly = true)
	public boolean isIdVerified(User user) {
		return checks.existsByUserAndMethodInAndStatus(user, ID_METHODS, IdentityStatus.VERIFIED);
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
