package com.rentrix.rentrixserver.admin.queue;

import com.rentrix.rentrixserver.admin.AdminConsoleMembers;
import com.rentrix.rentrixserver.admin.AuditLog;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.admin.ModerationTargetType;
import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.identity.IdentityMethod;
import com.rentrix.rentrixserver.identity.IdentityRejectCode;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.identity.IdentityStatus;
import com.rentrix.rentrixserver.identity.IdentityVerification;
import com.rentrix.rentrixserver.notification.sms.PhoneMasks;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.rating.RatingRecomputer;
import com.rentrix.rentrixserver.report.Report;
import com.rentrix.rentrixserver.report.ReportStatus;
import com.rentrix.rentrixserver.report.ReportTargetType;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewResponse;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.VerificationApplication;
import com.rentrix.rentrixserver.verification.VerificationDecisionCode;
import com.rentrix.rentrixserver.verification.VerificationState;
import com.rentrix.rentrixserver.verification.VerificationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The mobile-first approvals queue (D-134, D-136, W9): everything waiting for the green tick, newest first, and the
 * admin's answer to each card. Decisions reuse the existing paths: users through {@code /admin/applications} (B11), ID
 * checks through {@link IdentityService#decideByAdmin}, and homes, tenancies and reviews set their
 * {@code verification_status} (V7) with one audit row each (D-87). A review's tick change recomputes its ratings in
 * the same transaction (D-135). Every decision re-checks admin membership in the database (D-19) and refuses an
 * admin who is a party to the item ({@code 403 admin_conflict}, D-88).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminQueueService {

	/** Cards per type in one read; the queue is worked from the top, so older ones show as the newer ones go. */
	static final int MAX_PER_TYPE = 50;

	static final Set<IdentityMethod> ADMIN_CHECKED = EnumSet.of(IdentityMethod.PAN, IdentityMethod.AADHAAR,
		IdentityMethod.LINKEDIN);

	private static final Set<TenancyStatus> TICKABLE_TENANCIES = EnumSet.of(TenancyStatus.PENDING_CONFIRMATION,
		TenancyStatus.ACTIVE, TenancyStatus.ENDED);
	private static final Set<ReviewStatus> TICKABLE_REVIEWS = EnumSet.of(ReviewStatus.AWAITING_REVEAL,
		ReviewStatus.PUBLISHED);
	private static final List<String> ITEM_REJECT = codes(ItemRejectCode.CANNOT_CONFIRM, ItemRejectCode.DETAILS_WRONG,
		ItemRejectCode.DUPLICATE, ItemRejectCode.OTHER);
	/** Hide is for abuse only (D-51), so it takes only these. */
	private static final List<String> HIDE_REASONS = codes(ItemRejectCode.ABUSE, ItemRejectCode.PERSONAL_DATA,
		ItemRejectCode.OTHER);
	/** A review card offers both sets: the reject reasons, then the hide ones. */
	private static final List<String> REVIEW_REASONS = Stream.concat(ITEM_REJECT.stream(),
		HIDE_REASONS.stream().filter(code -> !ITEM_REJECT.contains(code))).toList();

	private final EntityManager em;
	private final UserRepository users;
	private final AdminConsoleMembers members;
	private final IdentityService identity;
	private final RatingRecomputer ratings;
	private final AuditLog audit;
	private final Clock clock;

	/** One decision's result: the item and where it stands now. */
	public record Decided(QueueType type, String id, String outcome) {
	}

	// ---------------------------------------------------------------- reading

	/**
	 * The queue, newest first (captain 2026-11-23). {@code type} null lists every approval type; open reports show only
	 * with {@code type=REPORT} (the console's Reports page). {@code city} (a city code) narrows users, ID checks, homes,
	 * tenancies and reviews to that city. Counts are per type under the same city filter and are not capped.
	 */
	@Transactional(readOnly = true)
	public QueueView queue(QueueType type, String city) {
		String cityCode = StringUtils.hasText(city) ? city.trim().toUpperCase(Locale.ROOT) : null;
		Map<QueueType, Long> counts = new EnumMap<>(QueueType.class);
		List<QueueItem> items = new ArrayList<>();
		for (QueueType t : QueueType.values()) {
			counts.put(t, count(t, cityCode));
			if (type == t || (type == null && t != QueueType.REPORT)) {
				items.addAll(items(t, cityCode));
			}
		}
		items.sort(Comparator.comparing(QueueItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
		return new QueueView(items, counts);
	}

	private long count(QueueType type, String city) {
		return switch (type) {
			case USER -> single("select count(a) from VerificationApplication a where a.status = :pending"
				+ cityClause("a.user.homeCity", city), Long.class, city, q -> q.setParameter("pending",
				VerificationStatus.PENDING));
			case IDENTITY -> single("select count(i) from IdentityVerification i where i.status = :pending"
				+ " and i.method in :methods" + cityClause("i.user.homeCity", city), Long.class, city,
				q -> q.setParameter("pending", IdentityStatus.PENDING).setParameter("methods", ADMIN_CHECKED));
			case PROPERTY -> single("select count(p) from Property p where " + PROPERTY_WAITING
				+ cityClause("p.city", city), Long.class, city, this::propertyParams);
			case TENANCY -> single("select count(t) from Tenancy t where " + TENANCY_WAITING
				+ cityClause("t.property.city", city), Long.class, city, this::tenancyParams);
			case REVIEW -> single("select count(r) from Review r where " + REVIEW_WAITING
				+ cityClause("r.tenancy.property.city", city), Long.class, city, this::reviewParams);
			case CITY -> city != null ? 0L : single("select count(c) from City c where c.live = false", Long.class,
				null, q -> q);
			case REPORT -> city != null ? 0L : single("select count(r) from Report r where r.status = :open",
				Long.class, null, q -> q.setParameter("open", ReportStatus.OPEN));
		};
	}

	private static final String PROPERTY_WAITING = "p.status = :published and p.deletedAt is null"
		+ " and (p.verificationStatus is null or p.verificationStatus = :unverified)";
	private static final String TENANCY_WAITING = "t.status in :tickable"
		+ " and (t.verificationStatus is null or t.verificationStatus = :unverified)";
	private static final String REVIEW_WAITING = "r.status in :tickable and r.deletedAt is null"
		+ " and (r.verificationStatus is null or r.verificationStatus = :unverified)";

	private <Q extends TypedQuery<?>> Q propertyParams(Q q) {
		q.setParameter("published", PropertyStatus.PUBLISHED).setParameter("unverified", VerificationState.UNVERIFIED);
		return q;
	}

	private <Q extends TypedQuery<?>> Q tenancyParams(Q q) {
		q.setParameter("tickable", TICKABLE_TENANCIES).setParameter("unverified", VerificationState.UNVERIFIED);
		return q;
	}

	private <Q extends TypedQuery<?>> Q reviewParams(Q q) {
		q.setParameter("tickable", TICKABLE_REVIEWS).setParameter("unverified", VerificationState.UNVERIFIED);
		return q;
	}

	private static String cityClause(String path, String city) {
		return city == null ? "" : " and " + path + ".code = :city";
	}

	private <T> T single(String jpql, Class<T> type, String city, Function<TypedQuery<T>, TypedQuery<T>> params) {
		TypedQuery<T> q = params.apply(em.createQuery(jpql, type));
		if (city != null) {
			q.setParameter("city", city);
		}
		return q.getSingleResult();
	}

	private <T> List<T> list(String jpql, Class<T> type, String city, Function<TypedQuery<T>, TypedQuery<T>> params) {
		TypedQuery<T> q = params.apply(em.createQuery(jpql, type));
		if (city != null) {
			q.setParameter("city", city);
		}
		return q.setMaxResults(MAX_PER_TYPE).getResultList();
	}

	private List<QueueItem> items(QueueType type, String city) {
		return switch (type) {
			case USER -> list("select a from VerificationApplication a where a.status = :pending"
				+ cityClause("a.user.homeCity", city) + " order by a.submittedAt desc", VerificationApplication.class,
				city, q -> q.setParameter("pending", VerificationStatus.PENDING)).stream().map(this::userCard).toList();
			case IDENTITY -> list("select i from IdentityVerification i where i.status = :pending and i.method in :methods"
				+ cityClause("i.user.homeCity", city) + " order by i.createdAt desc", IdentityVerification.class, city,
				q -> q.setParameter("pending", IdentityStatus.PENDING).setParameter("methods", ADMIN_CHECKED))
				.stream().map(this::identityCard).toList();
			case PROPERTY -> list("select p from Property p where " + PROPERTY_WAITING + cityClause("p.city", city)
				+ " order by p.createdAt desc", Property.class, city, this::propertyParams)
				.stream().map(this::propertyCard).toList();
			case TENANCY -> list("select t from Tenancy t where " + TENANCY_WAITING
				+ cityClause("t.property.city", city) + " order by t.createdAt desc", Tenancy.class, city,
				this::tenancyParams).stream().map(this::tenancyCard).toList();
			case REVIEW -> list("select r from Review r where " + REVIEW_WAITING
				+ cityClause("r.tenancy.property.city", city) + " order by r.createdAt desc", Review.class, city,
				this::reviewParams).stream().map(this::reviewCard).toList();
			case CITY -> city != null ? List.of() : list("select c from City c where c.live = false"
				+ " order by c.createdAt desc", City.class, null, q -> q).stream().map(this::cityCard).toList();
			case REPORT -> city != null ? List.of() : list("select r from Report r where r.status = :open"
				+ " order by r.createdAt desc", Report.class, null, q -> q.setParameter("open", ReportStatus.OPEN))
				.stream().map(this::reportCard).toList();
		};
	}

	// ---------------------------------------------------------------- cards

	private QueueItem userCard(VerificationApplication application) {
		User user = application.getUser();
		List<String> methods = em.createQuery("select i from IdentityVerification i where i.user = :user"
				+ " and i.status = :verified order by i.method", IdentityVerification.class)
			.setParameter("user", user).setParameter("verified", IdentityStatus.VERIFIED).getResultList().stream()
			.map(i -> methodLabel(i.getMethod()) + " verified").toList();
		List<String> details = new ArrayList<>();
		details.add(user.getEmail());
		if (user.getPhone() != null) {
			details.add(PhoneMasks.mask(user.getPhone()));
		}
		details.add(methods.isEmpty() ? "No verified method yet" : String.join(" · ", methods));
		return new QueueItem(QueueType.USER, application.getPublicId().toString(), user.getDisplayName(),
			roleLabel(application) + " · revision " + application.getRevision(), details, flags(application.getFlags()),
			cityCode(user.getHomeCity()), application.getSubmittedAt(), VerificationState.UNVERIFIED, null,
			List.of(QueueAction.APPROVE, QueueAction.REJECT), codes(VerificationDecisionCode.values()));
	}

	private QueueItem identityCard(IdentityVerification check) {
		User user = check.getUser();
		List<String> details = new ArrayList<>();
		details.add(user.getEmail());
		if (check.getMethod() == IdentityMethod.LINKEDIN) {
			details.add(check.getLinkedinUrl());
		} else if (check.getLast4() != null) {
			details.add(methodLabel(check.getMethod()) + " ending " + check.getLast4());
		}
		List<String> rejectCodes = new ArrayList<>(codes(IdentityRejectCode.ID_UNREADABLE,
			IdentityRejectCode.ID_NAME_MISMATCH));
		if (check.getMethod() == IdentityMethod.AADHAAR) {
			rejectCodes.add(IdentityRejectCode.AADHAAR_NOT_MASKED.name());
		}
		if (check.getMethod() == IdentityMethod.LINKEDIN) {
			rejectCodes = new ArrayList<>(List.of(IdentityRejectCode.LINKEDIN_MISMATCH.name()));
		}
		rejectCodes.add(IdentityRejectCode.OTHER.name());
		return new QueueItem(QueueType.IDENTITY, check.getPublicId().toString(), user.getDisplayName(),
			methodLabel(check.getMethod()) + " check", details.stream().filter(Objects::nonNull).toList(), List.of(),
			cityCode(user.getHomeCity()), check.getCreatedAt(), VerificationState.UNVERIFIED, null,
			List.of(QueueAction.APPROVE, QueueAction.REJECT), rejectCodes);
	}

	private QueueItem propertyCard(Property p) {
		List<String> details = new ArrayList<>();
		details.add(homeFacts(p));
		if (p.getAddedBy() != null) {
			details.add("Added by tenant " + p.getAddedBy().getDisplayName());
		} else if (p.getLandlord() != null && !UnclaimedLandlord.is(p.getLandlord())) {
			details.add("Owner " + p.getLandlord().getDisplayName());
		}
		return new QueueItem(QueueType.PROPERTY, p.getPublicId().toString(), homeLabel(p), cityName(p.getCity()),
			details, p.isDemo() ? List.of("DEMO") : List.of(), cityCode(p.getCity()), p.getCreatedAt(),
			VerificationState.of(p.getVerificationStatus()), "/p/" + p.getPublicId(),
			List.of(QueueAction.APPROVE, QueueAction.REJECT), ITEM_REJECT);
	}

	private QueueItem tenancyCard(Tenancy t) {
		Property p = t.getProperty();
		List<String> details = new ArrayList<>();
		details.add("Tenant " + name(t.getTenant()) + " · Owner " + name(t.getLandlord()));
		details.add("From " + t.getStartDate() + (t.getEndDate() == null ? "" : " to " + t.getEndDate())
			+ " · " + (t.getInitialRentInr() == null ? "Rent not shared" : "Rent " + rupees(t.getInitialRentInr()) + " / month"));
		details.add(t.getConfirmedAt() != null ? "Confirmed by both" : "Not confirmed by the other side");
		return new QueueItem(QueueType.TENANCY, t.getPublicId().toString(), homeLabel(p),
			cityName(p.getCity()) + " · " + statusLabel(t.getStatus()), details, List.of(), cityCode(p.getCity()),
			t.getCreatedAt(), VerificationState.of(t.getVerificationStatus()), "/p/" + p.getPublicId(),
			List.of(QueueAction.APPROVE, QueueAction.REJECT), ITEM_REJECT);
	}

	private QueueItem reviewCard(Review r) {
		Property p = r.getTenancy().getProperty();
		String about = switch (r.getDirection()) {
			case TENANT_TO_PROPERTY -> "Review of the home " + homeLabel(p);
			case TENANT_TO_LANDLORD -> "Review of the owner " + name(r.getSubjectUser());
			case LANDLORD_TO_TENANT -> "Review of the tenant " + name(r.getSubjectUser());
		};
		List<String> details = new ArrayList<>();
		details.add(r.getRatingOverall() + " ★ by " + name(r.getAuthor()));
		if (StringUtils.hasText(r.getBody())) {
			details.add(snippet(r.getBody()));
		}
		return new QueueItem(QueueType.REVIEW, r.getPublicId().toString(), about,
			cityName(p.getCity()) + (r.getStatus() == ReviewStatus.AWAITING_REVEAL ? " · waiting to be revealed" : ""),
			details, List.of(), cityCode(p.getCity()), r.getCreatedAt(), VerificationState.of(r.getVerificationStatus()),
			r.getDirection() == ReviewDirection.LANDLORD_TO_TENANT ? null : "/p/" + p.getPublicId(),
			List.of(QueueAction.APPROVE, QueueAction.REJECT, QueueAction.HIDE), REVIEW_REASONS);
	}

	private QueueItem cityCard(City c) {
		long homes = em.createQuery("select count(p) from Property p where p.city = :city and p.deletedAt is null",
			Long.class).setParameter("city", c).getSingleResult();
		return new QueueItem(QueueType.CITY, c.getCode(), c.getName(), c.getState(),
			List.of(homes + (homes == 1 ? " home" : " homes") + " waiting for this city"), List.of(), c.getCode(),
			c.getCreatedAt(), VerificationState.UNVERIFIED, null, List.of(QueueAction.APPROVE), List.of());
	}

	private QueueItem reportCard(Report r) {
		List<String> details = new ArrayList<>();
		details.add("Reported by " + name(r.getReporter()));
		if (StringUtils.hasText(r.getDetails())) {
			details.add(snippet(r.getDetails()));
		}
		List<QueueAction> actions = r.getTargetType() == ReportTargetType.REVIEW
			? List.of(QueueAction.APPROVE, QueueAction.HIDE) : List.of(QueueAction.APPROVE);
		return new QueueItem(QueueType.REPORT, r.getPublicId().toString(), label(r.getReason().name()),
			"About a " + label(r.getTargetType().name()).toLowerCase(Locale.ROOT), details, List.of(), null,
			r.getCreatedAt(), VerificationState.UNVERIFIED, null, actions, HIDE_REASONS);
	}

	// ---------------------------------------------------------------- deciding

	/**
	 * Applies one answer. {@code code} is the reason of a reject or hide (one of the card's {@code rejectCodes});
	 * {@code note} is optional and shown to the owner with it.
	 */
	@Transactional
	public Decided decide(UUID adminId, QueueType type, String id, QueueAction action, String code, String note) {
		User admin = admin(adminId);
		String cleanNote = StringUtils.hasText(note) ? note.trim() : null;
		return switch (type) {
			case USER -> throw ApiException.rule("action_not_allowed",
				"Users are decided through /admin/applications/{id}/approve or /reject.");
			case IDENTITY -> decideIdentity(admin, id, action, code, cleanNote);
			case PROPERTY -> decideProperty(admin, id, action, code, cleanNote);
			case TENANCY -> decideTenancy(admin, id, action, code, cleanNote);
			case REVIEW -> decideReview(admin, id, action, code, cleanNote);
			case CITY -> decideCity(admin, id, action);
			case REPORT -> decideReport(admin, id, action, code, cleanNote);
		};
	}

	private Decided decideIdentity(User admin, String id, QueueAction action, String code, String note) {
		IdentityVerification check = em.createQuery("select i from IdentityVerification i where i.publicId = :id",
				IdentityVerification.class).setParameter("id", uuid(id)).getResultStream().findFirst()
			.orElseThrow(() -> ApiException.notFound("Identity check"));
		notSelf(admin, check.getUser());
		IdentityVerification decided = switch (action) {
			case APPROVE -> identity.decideByAdmin(admin, check.getUser(), check.getPublicId(), IdentityStatus.VERIFIED,
				null, note);
			case REJECT -> identity.decideByAdmin(admin, check.getUser(), check.getPublicId(), IdentityStatus.REJECTED,
				parse(IdentityRejectCode.class, code), note);
			case HIDE -> throw notAllowed(QueueType.IDENTITY, action);
		};
		return new Decided(QueueType.IDENTITY, id, decided.getStatus().name());
	}

	private Decided decideProperty(User admin, String id, QueueAction action, String code, String note) {
		Property p = em.createQuery("select p from Property p where p.publicId = :id and p.deletedAt is null",
				Property.class).setParameter("id", uuid(id)).getResultStream().findFirst()
			.orElseThrow(() -> ApiException.notFound("Home"));
		notSelf(admin, p.getLandlord());
		notSelf(admin, p.getAddedBy());
		VerificationState state = tickState(QueueType.PROPERTY, action);
		if (!p.getStatus().isPublic()) {
			throw ApiException.conflict("invalid_transition", "Only a home on the site can get the green tick.");
		}
		notAlready(p.getVerificationStatus(), state);
		String reason = reason(state, code, note, ITEM_REJECT);
		p.setVerificationStatus(state);
		audit.record(admin, state == VerificationState.VERIFIED ? ModerationActionType.APPROVE_PROPERTY
			: ModerationActionType.REJECT_PROPERTY, ModerationTargetType.PROPERTY, p.getId(), id, reason);
		log.info("Queue: home {} {} by admin {}", id, state, admin.getPublicId());
		return new Decided(QueueType.PROPERTY, id, state.name());
	}

	private Decided decideTenancy(User admin, String id, QueueAction action, String code, String note) {
		Tenancy t = em.createQuery("select t from Tenancy t where t.publicId = :id", Tenancy.class)
			.setParameter("id", uuid(id)).getResultStream().findFirst()
			.orElseThrow(() -> ApiException.notFound("Tenancy"));
		notSelf(admin, t.getLandlord());
		notSelf(admin, t.getTenant());
		VerificationState state = tickState(QueueType.TENANCY, action);
		if (!TICKABLE_TENANCIES.contains(t.getStatus())) {
			throw ApiException.conflict("invalid_transition", "This tenancy was declined, cancelled or voided.");
		}
		notAlready(t.getVerificationStatus(), state);
		String reason = reason(state, code, note, ITEM_REJECT);
		t.setVerificationStatus(state);
		t.setVerificationDecidedBy(admin);
		t.setVerificationDecidedAt(now());
		t.setVerificationReason(reason);
		audit.record(admin, state == VerificationState.VERIFIED ? ModerationActionType.VERIFY_ITEM
			: ModerationActionType.REJECT_ITEM, ModerationTargetType.TENANCY, t.getId(), id, reason);
		log.info("Queue: tenancy {} {} by admin {}", id, state, admin.getPublicId());
		return new Decided(QueueType.TENANCY, id, state.name());
	}

	private Decided decideReview(User admin, String id, QueueAction action, String code, String note) {
		Review r = review(uuid(id));
		reviewConflict(admin, r);
		if (action == QueueAction.HIDE) {
			hide(admin, r, code, note);
			return new Decided(QueueType.REVIEW, id, ReviewStatus.HIDDEN.name());
		}
		VerificationState state = tickState(QueueType.REVIEW, action);
		if (!TICKABLE_REVIEWS.contains(r.getStatus())) {
			throw ApiException.conflict("invalid_transition", "This review is hidden or withdrawn.");
		}
		notAlready(r.getVerificationStatus(), state);
		String reason = reason(state, code, note, ITEM_REJECT);
		r.setVerificationStatus(state);
		r.setVerificationDecidedBy(admin);
		r.setVerificationDecidedAt(now());
		r.setVerificationReason(reason);
		ratings.reviewChanged(r);
		audit.record(admin, state == VerificationState.VERIFIED ? ModerationActionType.VERIFY_ITEM
			: ModerationActionType.REJECT_ITEM, ModerationTargetType.REVIEW, r.getId(), id, reason);
		log.info("Queue: review {} {} by admin {}", id, state, admin.getPublicId());
		return new Decided(QueueType.REVIEW, id, state.name());
	}

	/** Abuse only (D-51): removed from public view and from the ratings; the author sees the reason. */
	private void hide(User admin, Review r, String code, String note) {
		if (r.getStatus() == ReviewStatus.HIDDEN) {
			throw ApiException.conflict("invalid_transition", "This review is already hidden.");
		}
		String reason = reason(VerificationState.REJECTED, code, note, HIDE_REASONS);
		r.setStatus(ReviewStatus.HIDDEN);
		r.setHiddenAt(now());
		r.setHiddenReason(reason);
		ratings.reviewChanged(r);
		audit.record(admin, ModerationActionType.HIDE_REVIEW, ModerationTargetType.REVIEW, r.getId(),
			r.getPublicId().toString(), reason);
		log.info("Queue: review {} hidden by admin {}", r.getPublicId(), admin.getPublicId());
	}

	private Decided decideCity(User admin, String code, QueueAction action) {
		if (action != QueueAction.APPROVE) {
			throw notAllowed(QueueType.CITY, action);
		}
		City city = em.find(City.class, code.trim().toUpperCase(Locale.ROOT));
		if (city == null) {
			throw ApiException.notFound("City");
		}
		if (city.isLive()) {
			throw ApiException.conflict("invalid_transition", "This city is already live.");
		}
		city.goLive();
		audit.record(admin, ModerationActionType.ACTIVATE_CITY, ModerationTargetType.CITY, null, city.getCode(), null);
		log.info("Queue: city {} set live by admin {}", city.getCode(), admin.getPublicId());
		return new Decided(QueueType.CITY, city.getCode(), "LIVE");
	}

	private Decided decideReport(User admin, String id, QueueAction action, String code, String note) {
		Report report = em.createQuery("select r from Report r where r.publicId = :id", Report.class)
			.setParameter("id", uuid(id)).getResultStream().findFirst()
			.orElseThrow(() -> ApiException.notFound("Report"));
		if (report.getStatus() != ReportStatus.OPEN) {
			throw ApiException.conflict("invalid_transition", "This report is already handled.");
		}
		notSelf(admin, report.getReporter());
		reportConflict(admin, report);
		ReportStatus outcome;
		switch (action) {
			case APPROVE -> {
				outcome = ReportStatus.DISMISSED;
				audit.record(admin, ModerationActionType.DISMISS_REPORT, targetType(report.getTargetType()),
					report.getTargetId(), report.getPublicId().toString(), note);
			}
			case HIDE -> {
				if (report.getTargetType() != ReportTargetType.REVIEW) {
					throw notAllowed(QueueType.REPORT, action);
				}
				Review r = em.find(Review.class, report.getTargetId());
				if (r == null) {
					throw ApiException.notFound("Review");
				}
				// Another report on the same review may have hidden it already: this one is then closed as well.
				if (r.getStatus() != ReviewStatus.HIDDEN) {
					hide(admin, r, code, note);
				} else {
					reason(VerificationState.REJECTED, code, note, HIDE_REASONS);
				}
				outcome = ReportStatus.ACTIONED;
			}
			default -> throw notAllowed(QueueType.REPORT, action);
		}
		report.setStatus(outcome);
		report.setHandledBy(admin);
		report.setHandledAt(now());
		report.setResolutionNote(note);
		return new Decided(QueueType.REPORT, id, outcome.name());
	}

	// ---------------------------------------------------------------- helpers

	/** The caller must be an admin console member now, not only in the token (D-19). */
	private User admin(UUID adminId) {
		return users.findByPublicId(adminId).filter(members::isMember)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "forbidden", "You are not allowed to do this."));
	}

	private Review review(UUID id) {
		return em.createQuery("select r from Review r where r.publicId = :id and r.deletedAt is null", Review.class)
			.setParameter("id", id).getResultStream().findFirst()
			.orElseThrow(() -> ApiException.notFound("Review"));
	}

	private static void reviewConflict(User admin, Review r) {
		notSelf(admin, r.getAuthor());
		notSelf(admin, r.getSubjectUser());
		notSelf(admin, r.getTenancy().getLandlord());
		notSelf(admin, r.getTenancy().getTenant());
	}

	private void reportConflict(User admin, Report report) {
		Long target = report.getTargetId();
		if (target == null) {
			return;
		}
		switch (report.getTargetType()) {
			case REVIEW -> {
				Review r = em.find(Review.class, target);
				if (r != null) {
					reviewConflict(admin, r);
				}
			}
			case REVIEW_RESPONSE -> {
				ReviewResponse response = em.find(ReviewResponse.class, target);
				if (response != null) {
					notSelf(admin, response.getAuthor());
					reviewConflict(admin, response.getReview());
				}
			}
			case USER -> notSelf(admin, em.find(User.class, target));
			case PROPERTY -> {
				Property p = em.find(Property.class, target);
				if (p != null) {
					notSelf(admin, p.getLandlord());
					notSelf(admin, p.getAddedBy());
				}
			}
			case TENANCY -> {
				Tenancy t = em.find(Tenancy.class, target);
				if (t != null) {
					notSelf(admin, t.getLandlord());
					notSelf(admin, t.getTenant());
				}
			}
		}
	}

	/** D-88: an admin never decides on something they are a party to. */
	private static void notSelf(User admin, User party) {
		if (party != null && party.getId().equals(admin.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "admin_conflict",
				"You are a party to this item. Another admin must decide it.");
		}
	}

	/**
	 * A second tap, or a card that another admin answered meanwhile: the same answer twice is {@code 409}. A different
	 * answer is allowed, so an admin can take a tick back or give it after a rejection (both audited).
	 */
	private static void notAlready(VerificationState current, VerificationState wanted) {
		if (VerificationState.of(current) == wanted) {
			throw ApiException.conflict("invalid_transition", wanted == VerificationState.VERIFIED
				? "This item already has the green tick." : "This item is already rejected.");
		}
	}
	
	private static VerificationState tickState(QueueType type, QueueAction action) {
		return switch (action) {
			case APPROVE -> VerificationState.VERIFIED;
			case REJECT -> VerificationState.REJECTED;
			case HIDE -> throw notAllowed(type, action);
		};
	}

	/** The stored reason: none for an approval (a note if given), else {@code CODE} or {@code CODE: note}. */
	private static String reason(VerificationState state, String code, String note, List<String> allowed) {
		if (state == VerificationState.VERIFIED) {
			return note;
		}
		if (code == null || !allowed.contains(code)) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("code", "invalid",
				"Pick a reason: " + String.join(", ", allowed) + ".")));
		}
		return note == null ? code : code + ": " + note;
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String code) {
		try {
			return Enum.valueOf(type, code == null ? "" : code.trim());
		} catch (IllegalArgumentException e) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("code", "invalid",
				"Pick a reason: " + String.join(", ", codes(type.getEnumConstants())) + ".")));
		}
	}

	private static UUID uuid(String id) {
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			throw ApiException.notFound("Item");
		}
	}

	private static ApiException notAllowed(QueueType type, QueueAction action) {
		return ApiException.rule("action_not_allowed",
			"A " + label(type.name()).toLowerCase(Locale.ROOT) + " card cannot be answered with "
				+ action.name().toLowerCase(Locale.ROOT) + ".");
	}

	private static ModerationTargetType targetType(ReportTargetType type) {
		return ModerationTargetType.valueOf(type.name());
	}

	@SafeVarargs
	private static <E extends Enum<E>> List<String> codes(E... values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}

	private static List<String> flags(String flags) {
		return flags == null ? List.of() : Stream.of(flags.split(",")).map(String::trim).filter(s -> !s.isEmpty())
			.toList();
	}

	private static String homeLabel(Property p) {
		String label = Stream.of(p.getBuildingName(), p.getLocality()).filter(StringUtils::hasText)
			.reduce((a, b) -> a + ", " + b).orElse("A home");
		return label;
	}

	private static String homeFacts(Property p) {
		List<String> facts = new ArrayList<>();
		if (p.getBedrooms() != null) {
			facts.add(p.getBedrooms() + " BHK");
		}
		if (p.getPropertyType() != null) {
			facts.add(label(p.getPropertyType().name()));
		}
		if (p.getPincode() != null) {
			facts.add("PIN " + p.getPincode());
		}
		return facts.isEmpty() ? "No details yet" : String.join(" · ", facts);
	}

	private static String name(User user) {
		if (user == null) {
			return "not on the site";
		}
		return UnclaimedLandlord.is(user) ? "not on the site yet" : user.getDisplayName();
	}

	private static String roleLabel(VerificationApplication application) {
		return label(application.getRole().name()) + " verification";
	}

	private static String methodLabel(IdentityMethod method) {
		return switch (method) {
			case PAN -> "PAN";
			case AADHAAR -> "Aadhaar";
			case LINKEDIN -> "LinkedIn";
			case PHONE_OTP, TRUECALLER -> "Phone";
			case TELEGRAM -> "Phone (Telegram)";
		};
	}

	private static String statusLabel(TenancyStatus status) {
		return switch (status) {
			case PENDING_CONFIRMATION -> "waiting for the other side";
			case ACTIVE -> "current";
			case ENDED -> "ended";
			default -> label(status.name()).toLowerCase(Locale.ROOT);
		};
	}

	private static String cityName(City city) {
		return city == null ? "" : city.getName();
	}

	private static String cityCode(City city) {
		return city == null ? null : city.getCode();
	}

	private static String rupees(long amount) {
		return "₹" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN")).format(amount);
	}

	private static String snippet(String text) {
		String clean = text.trim().replaceAll("\\s+", " ");
		return clean.length() <= 200 ? clean : clean.substring(0, 199).trim() + "…";
	}

	/** {@code TENANT_TO_LANDLORD} → "Tenant to landlord". */
	static String label(String constant) {
		String words = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}

}
