package com.rentrix.rentrixserver.seed;

import com.rentrix.rentrixserver.admin.ModerationAction;
import com.rentrix.rentrixserver.admin.ModerationActionRepository;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.admin.ModerationTargetType;
import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.identity.CheckedBy;
import com.rentrix.rentrixserver.identity.IdHasher;
import com.rentrix.rentrixserver.identity.IdentityMethod;
import com.rentrix.rentrixserver.identity.IdentityRejectCode;
import com.rentrix.rentrixserver.identity.IdentityStatus;
import com.rentrix.rentrixserver.identity.IdentityVerification;
import com.rentrix.rentrixserver.identity.IdentityVerificationRepository;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.VerificationApplication;
import com.rentrix.rentrixserver.verification.VerificationApplicationRepository;
import com.rentrix.rentrixserver.verification.VerificationDecisionCode;
import com.rentrix.rentrixserver.verification.VerificationEvent;
import com.rentrix.rentrixserver.verification.VerificationEventRepository;
import com.rentrix.rentrixserver.verification.VerificationEventType;
import com.rentrix.rentrixserver.verification.VerificationStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Verification part of the dev seed (D-19, D-97, D-115), run by {@link DemoSeeder} inside its transaction:
 * <ul>
 * <li>the demo admin {@code demo-admin@example.test}, {@code ADMIN} in {@code user_roles} (the DB assignment);</li>
 * <li>every seeded landlord and tenant {@code APPROVED} with one {@code VERIFIED} ID method, spread over PAN, masked
 * Aadhaar and phone, and LinkedIn verified for Landlord BLR-1, Landlord PNQ-2 and Tenant PNQ-2;</li>
 * <li>the admin queue: Landlord BLR-3 (PAN and LinkedIn pending), Tenant BLR-7 (PAN pending, {@code DUPLICATE_ID}
 * with BLR-8), Tenant PNQ-7 (phone verified, masked Aadhaar pending) all {@code PENDING}; Tenant BLR-8
 * {@code REJECTED} with {@code ID_UNREADABLE}, free to resubmit.</li>
 * </ul>
 * Only fake masked values: last 4 like {@code 001P}, HMACs of fake numbers from the dev {@code ID_HASH_KEY},
 * {@code users.phone} stays null. Every decision has its event and audit row by the demo admin.
 */
class DemoVerificationSeed {
	
	static final String ADMIN_EMAIL = "demo-admin@example.test";
	
	/** Demo users who wait in (or were turned away from) the admin queue; no tenancies, no reviews. */
	static final List<String> QUEUE = List.of("Demo Landlord BLR-3", "Demo Tenant BLR-7", "Demo Tenant PNQ-7",
		"Demo Tenant BLR-8");
	
	private static final Set<String> LINKEDIN_VERIFIED =
		Set.of("Demo Landlord BLR-1", "Demo Landlord PNQ-2", "Demo Tenant PNQ-2");
	
	/** A fake PAN shared by BLR-7 and BLR-8, to show the duplicate flag (D-102). Never a real number. */
	private static final String SHARED_FAKE_PAN = "DEMOT0008T";
	
	private final UserRepository users;
	private final CityRepository cities;
	private final VerificationApplicationRepository applications;
	private final VerificationEventRepository events;
	private final IdentityVerificationRepository checks;
	private final ModerationActionRepository audit;
	private final IdHasher hasher;
	private final Instant seededAt;
	private final Instant now;
	private int serial;
	
	DemoVerificationSeed(UserRepository users, CityRepository cities, VerificationApplicationRepository applications,
		VerificationEventRepository events, IdentityVerificationRepository checks, ModerationActionRepository audit,
		IdHasher hasher, Instant seededAt) {
		this.users = users;
		this.cities = cities;
		this.applications = applications;
		this.events = events;
		this.checks = checks;
		this.audit = audit;
		this.hasher = hasher;
		this.seededAt = seededAt;
		this.now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
	}
	
	/** Call after the demo landlords and tenants exist; returns the number of applications written. */
	int seed() {
		List<User> parties = users.findAll().stream()
			.filter(User::isDemo)
			.sorted(Comparator.comparing(User::getDisplayName))
			.toList();
		User admin = admin();
		int count = 0;
		int tenantIndex = 0;
		for (User party : parties) {
			for (Role role : List.of(Role.LANDLORD, Role.TENANT)) {
				if (!party.hasRole(role)) {
					continue;
				}
				IdentityMethod method = role == Role.LANDLORD ? landlordMethod(party) : tenantMethod(tenantIndex++);
				verified(party, method, admin, seededAt);
				approved(party, role, admin);
				count++;
			}
			if (LINKEDIN_VERIFIED.contains(party.getDisplayName())) {
				linkedin(party, IdentityStatus.VERIFIED, admin);
			}
		}
	
		User blr3 = person("Demo Landlord BLR-3", "demo-landlord-blr-3", Role.LANDLORD, "BLR");
		pending(blr3, IdentityMethod.PAN, "003L", hasher.hmac("DEMOL0003L"));
		linkedin(blr3, IdentityStatus.PENDING, null);
		submitted(blr3, Role.LANDLORD, Set.of(), now.minus(Duration.ofHours(30)));
	
		User blr7 = person("Demo Tenant BLR-7", "demo-tenant-blr-7", Role.TENANT, "BLR");
		pending(blr7, IdentityMethod.PAN, SHARED_FAKE_PAN.substring(6), hasher.hmac(SHARED_FAKE_PAN));
		linkedin(blr7, IdentityStatus.PENDING, null);
		submitted(blr7, Role.TENANT, Set.of("DUPLICATE_ID"), now.minus(Duration.ofHours(20)));
	
		User pnq7 = person("Demo Tenant PNQ-7", "demo-tenant-pnq-7", Role.TENANT, "PNQ");
		verified(pnq7, IdentityMethod.PHONE_OTP, null, now.minus(Duration.ofHours(8)));
		pending(pnq7, IdentityMethod.AADHAAR, "0007", null);
		submitted(pnq7, Role.TENANT, Set.of(), now.minus(Duration.ofHours(6)));
	
		User blr8 = person("Demo Tenant BLR-8", "demo-tenant-blr-8", Role.TENANT, "BLR");
		rejected(blr8, admin);
		return count + QUEUE.size();
	}
	
	private User admin() {
		User admin = new User();
		admin.setDisplayName("Demo Admin");
		admin.setEmail(ADMIN_EMAIL);
		admin.setEmailVerifiedAt(seededAt);
		admin.setOnboardedAt(seededAt);
		admin.setRoles(EnumSet.of(Role.ADMIN));
		admin.setDemo(true);
		admin.setCreatedAt(seededAt);
		return users.save(admin);
	}
	
	private User person(String name, String emailLocalPart, Role role, String city) {
		User user = new User();
		user.setDisplayName(name);
		user.setEmail(emailLocalPart.toLowerCase(Locale.ROOT) + "@example.test");
		user.setEmailVerifiedAt(seededAt);
		user.setOnboardedAt(seededAt);
		user.setRoles(EnumSet.of(role));
		user.setHomeCity(cities.getReferenceById(city));
		user.setDemo(true);
		user.setCreatedAt(seededAt);
		return users.save(user);
	}
	
	/** D-115: landlords BLR-1 PAN, BLR-2 masked Aadhaar, PNQ-1 PAN, PNQ-2 phone; others PAN. */
	private static IdentityMethod landlordMethod(User landlord) {
		String name = landlord.getDisplayName();
		if (name.endsWith("BLR-2")) {
			return IdentityMethod.AADHAAR;
		}
		if (name.endsWith("PNQ-2")) {
			return IdentityMethod.PHONE_OTP;
		}
		return IdentityMethod.PAN;
	}
	
	/** D-115: tenants alternate PAN, phone and masked Aadhaar. */
	private static IdentityMethod tenantMethod(int index) {
		return List.of(IdentityMethod.PAN, IdentityMethod.PHONE_OTP, IdentityMethod.AADHAAR).get(index % 3);
	}
	
	/** Fake last 4, numbered in seed order: {@code 001P} for PAN, {@code 0001} for phone and Aadhaar. */
	private String fakeLast4(IdentityMethod method) {
		String digits = String.format(Locale.ROOT, "%03d", ++serial);
		return method == IdentityMethod.PAN ? digits + "P" : "0" + digits;
	}
	
	private void verified(User user, IdentityMethod method, User admin, Instant at) {
		IdentityVerification row = check(user, method, IdentityStatus.VERIFIED);
		row.setLast4(fakeLast4(method));
		if (method != IdentityMethod.AADHAAR) {
			// HMAC of a fake value only, so duplicate checks work on demo rows (D-115).
			row.setValueHmac(hasher.hmac("demo-" + method + "-" + user.getPublicId()));
		}
		row.setCheckedBy(method == IdentityMethod.PHONE_OTP ? CheckedBy.OTP : CheckedBy.ADMIN);
		row.setVerifiedAt(at);
		if (method != IdentityMethod.PHONE_OTP) {
			row.setVerifiedBy(admin);
		}
		row.setCreatedAt(at);
		checks.save(row);
		if (method != IdentityMethod.PHONE_OTP) {
			audited(admin, ModerationActionType.VERIFY_IDENTITY, ModerationTargetType.IDENTITY, row.getId(),
				row.getPublicId().toString(), null, at);
		}
	}
	
	private void pending(User user, IdentityMethod method, String last4, String hmac) {
		IdentityVerification row = check(user, method, IdentityStatus.PENDING);
		row.setLast4(last4);
		row.setValueHmac(hmac);
		row.setCreatedAt(now.minus(Duration.ofHours(31)));
		checks.save(row);
	}
	
	private void linkedin(User user, IdentityStatus status, User admin) {
		IdentityVerification row = check(user, IdentityMethod.LINKEDIN, status);
		row.setLinkedinUrl("https://www.linkedin.com/in/" + user.getEmail().substring(0, user.getEmail().indexOf('@')));
		if (status == IdentityStatus.VERIFIED) {
			row.setCheckedBy(CheckedBy.ADMIN);
			row.setVerifiedAt(seededAt);
			row.setVerifiedBy(admin);
		}
		checks.save(row);
	}
	
	private static IdentityVerification check(User user, IdentityMethod method, IdentityStatus status) {
		IdentityVerification row = new IdentityVerification();
		row.setUser(user);
		row.setMethod(method);
		row.setStatus(status);
		return row;
	}
	
	private void approved(User user, Role role, User admin) {
		VerificationApplication application = application(user, role, VerificationStatus.APPROVED, Set.of(),
			seededAt);
		application.setDecidedAt(seededAt);
		application.setDecidedBy(admin);
		applications.save(application);
		event(application, VerificationEventType.SUBMITTED, user, null, null, seededAt);
		event(application, VerificationEventType.APPROVED, admin, null, null, seededAt);
		audited(admin, ModerationActionType.APPROVE_APPLICATION, ModerationTargetType.APPLICATION, application.getId(),
			application.getPublicId().toString(), null, seededAt);
	}
	
	private void submitted(User user, Role role, Set<String> flags, Instant at) {
		VerificationApplication application = application(user, role, VerificationStatus.PENDING, flags, at);
		applications.save(application);
		event(application, VerificationEventType.SUBMITTED, user, null, null, at);
	}
	
	/** BLR-8: the PAN photo was unreadable; the PAN check and the application are both rejected (D-97). */
	private void rejected(User user, User admin) {
		Instant submittedAt = now.minus(Duration.ofDays(3));
		Instant decidedAt = now.minus(Duration.ofDays(2));
		String note = "The PAN photo is blurred. Upload a sharper photo.";
		IdentityVerification pan = check(user, IdentityMethod.PAN, IdentityStatus.REJECTED);
		pan.setLast4(SHARED_FAKE_PAN.substring(6));
		pan.setValueHmac(hasher.hmac(SHARED_FAKE_PAN));
		pan.setCheckedBy(CheckedBy.ADMIN);
		pan.setRejectCode(IdentityRejectCode.ID_UNREADABLE);
		pan.setRejectNote(note);
		pan.setCreatedAt(submittedAt);
		checks.save(pan);
		VerificationApplication application = application(user, Role.TENANT, VerificationStatus.REJECTED,
			Set.of("DUPLICATE_ID"), submittedAt);
		application.setDecidedAt(decidedAt);
		application.setDecidedBy(admin);
		application.setDecisionCode(VerificationDecisionCode.ID_UNREADABLE);
		application.setDecisionNote(note);
		applications.save(application);
		event(application, VerificationEventType.SUBMITTED, user, null, null, submittedAt);
		event(application, VerificationEventType.IDENTITY_CHECKED, admin, "PAN:REJECTED", note, decidedAt);
		event(application, VerificationEventType.REJECTED, admin, VerificationDecisionCode.ID_UNREADABLE.name(), note,
			decidedAt);
		audited(admin, ModerationActionType.REJECT_IDENTITY, ModerationTargetType.IDENTITY, pan.getId(),
			pan.getPublicId().toString(), IdentityRejectCode.ID_UNREADABLE + ": " + note, decidedAt);
		audited(admin, ModerationActionType.REJECT_APPLICATION, ModerationTargetType.APPLICATION, application.getId(),
			application.getPublicId().toString(), VerificationDecisionCode.ID_UNREADABLE + ": " + note, decidedAt);
	}
	
	private static VerificationApplication application(User user, Role role, VerificationStatus status,
		Set<String> flags, Instant submittedAt) {
		VerificationApplication application = new VerificationApplication();
		application.setUser(user);
		application.setRole(role);
		application.setStatus(status);
		application.setFlagSet(flags);
		application.setSubmittedAt(submittedAt);
		application.setCreatedAt(submittedAt);
		return application;
	}
	
	private void event(VerificationApplication application, VerificationEventType type, User actor, String code,
		String note, Instant at) {
		VerificationEvent event = new VerificationEvent();
		event.setApplication(application);
		event.setRevision(application.getRevision());
		event.setEvent(type);
		event.setActor(actor);
		event.setCode(code);
		event.setNote(note);
		event.setCreatedAt(at);
		events.save(event);
	}
	
	private void audited(User admin, ModerationActionType action, ModerationTargetType type, Long targetId,
		String targetRef, String reason, Instant at) {
		ModerationAction row = new ModerationAction();
		row.setAdmin(admin);
		row.setAction(action);
		row.setTargetType(type);
		row.setTargetId(targetId);
		row.setTargetRef(targetRef);
		row.setReason(reason);
		row.setCreatedAt(at);
		audit.save(row);
	}
	
}
