package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.edit.EditHistory;
import com.rentrix.rentrixserver.edit.EditTarget;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.identity.TrustBadges;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.rating.RatingBreakdowns;
import com.rentrix.rentrixserver.rating.RatingDetail;
import com.rentrix.rentrixserver.rating.RatingRecomputer;
import com.rentrix.rentrixserver.review.dto.CreateReviewRequest;
import com.rentrix.rentrixserver.review.dto.ReviewFormRequest;
import com.rentrix.rentrixserver.review.dto.ReviewFormView;
import com.rentrix.rentrixserver.review.dto.ReviewTask;
import com.rentrix.rentrixserver.review.dto.ReviewView;
import com.rentrix.rentrixserver.review.dto.UpdateReviewRequest;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.EvidenceLevel;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyService;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.tenancy.dto.PartyRef;
import com.rentrix.rentrixserver.user.PublicLabels;
import com.rentrix.rentrixserver.user.PublicNames;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.StayLength;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import com.rentrix.rentrixserver.user.profile.ProfileAccess;
import com.rentrix.rentrixserver.verification.ApprovalGuard;
import com.rentrix.rentrixserver.verification.VerificationState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reviews tied to a confirmed tenancy (§7). Trust rules: only a party of an {@code ACTIVE} or {@code ENDED}
 * tenancy writes, in the directions of their side (D-37), once per direction (D-38), inside the review window
 * (D-39), with an approved verification for that side (D-91), an email-verified account at least 7 days old for
 * the first review and at most {@value #MAX_PER_DAY} reviews a day (D-45), and text that passes the D-43 rules.
 * <p>States (§7.2): a property review publishes at once (D-47); a person review waits in {@code AWAITING_REVEAL},
 * visible only to its author, until the counter-review of the same tenancy is submitted or 14 days pass; then both publish together (D-46, {@link ReviewRevealJob}). Until it publishes, the author may edit for 7
 * days after submitting (D-41), and a review written during the tenancy may be updated once after it ends (D-40); a
 * published review is frozen. Withdrawal is final and never gated. The subject (or the home's landlord for a property
 * review) may post one response.
 * <p>Every change that can move a rating goes through {@link RatingRecomputer#reviewChanged} in the same
 * transaction (D-33). Reading is never gated by approval (D-91); visibility follows the review's subject (§10).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

	public static final Duration EDIT_WINDOW = Duration.ofDays(7);
	public static final Duration REVEAL_AFTER = Duration.ofDays(14);
	public static final int MAX_PER_DAY = 10;

	static final Map<TenancyParty, Set<ReviewDirection>> DIRECTIONS = Map.of(
		TenancyParty.TENANT, EnumSet.of(ReviewDirection.TENANT_TO_LANDLORD, ReviewDirection.TENANT_TO_PROPERTY),
		TenancyParty.LANDLORD, EnumSet.of(ReviewDirection.LANDLORD_TO_TENANT));

	private static final List<TenancyStatus> CONFIRMED = List.of(TenancyStatus.ACTIVE, TenancyStatus.ENDED);
	private static final Set<ReviewStatus> EDITABLE = EnumSet.of(ReviewStatus.AWAITING_REVEAL, ReviewStatus.PUBLISHED);
	/** The counterparty of an owner's review task while the tenancy has no linked tenant. */
	static final String YOUR_TENANT = "Your tenant";

	private final ReviewRepository reviews;
	private final ReviewRevisionRepository revisions;
	private final ReviewResponseRepository responses;
	private final RatingAggregateRepository aggregates;
	private final TenancyRepository tenancies;
	private final PropertyRepository properties;
	private final UserRepository users;
	private final ApprovalGuard approvals;
	private final IdentityService identity;
	private final ProfileAccess access;
	private final RatingRecomputer recomputer;
	private final RatingBreakdowns breakdowns;
	private final ReviewNotifier notifier;
	private final EditHistory history;
	private final Clock clock;

	// --------------------------------------------------------------- writes

	/** {@code POST /tenancies/{id}/reviews}. */
	@Transactional
	public ReviewView create(UUID tenancyId, CreateReviewRequest request, AuthenticatedUser caller) {
		User me = lockAuthor(caller);
		Tenancy tenancy = lockTenancy(tenancyId, me, caller);
		ReviewDirection direction = request.direction();
		requireCanWrite(tenancy, me, direction);
		requireVolume(me, 1);
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		Content content = content("", direction, false, request.ratingOverall(), request.ratingA(),
			request.ratingB(), request.ratingC(), null, null, request.body(), null, null,
			tenancy.getProperty().getUnitLabel(), errors);
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		return view(write(tenancy, me, direction, content), me, caller.hasRole(Role.ADMIN));
	}

	/**
	 * {@code POST /tenancies/{id}/review-form} (D-157): the tenant's home and landlord reviews, both saved or neither,
	 * or the owner's review of the tenant (LT-6). Each part passes the same rules as {@link #create}; every broken
	 * field rule of every part comes back in one {@code 422}, each on its own path. The tenant part takes no pros or
	 * cons ({@code not_allowed}): all text about a person stays behind the reveal (D-46).
	 */
	@Transactional
	public ReviewFormView form(UUID tenancyId, ReviewFormRequest request, AuthenticatedUser caller) {
		User me = lockAuthor(caller);
		Tenancy tenancy = lockTenancy(tenancyId, me, caller);
		Map<ReviewDirection, ReviewFormRequest.Part> parts = new LinkedHashMap<>();
		if (request.home() != null) {
			parts.put(ReviewDirection.TENANT_TO_PROPERTY, request.home());
		}
		if (request.landlord() != null) {
			parts.put(ReviewDirection.TENANT_TO_LANDLORD, request.landlord());
		}
		if (request.tenant() != null) {
			parts.put(ReviewDirection.LANDLORD_TO_TENANT, request.tenant());
		}
		if (parts.isEmpty()) {
			throw ApiException.validation(List.of(sideOf(tenancy, me) == TenancyParty.LANDLORD
				? new ApiProblem.FieldError("tenant", "not_null", "Rate the tenant.")
				: new ApiProblem.FieldError("home", "not_null", "Rate the home or the landlord.")));
		}
		parts.keySet().forEach(direction -> requireCanWrite(tenancy, me, direction));
		requireVolume(me, parts.size());
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		Map<ReviewDirection, Content> contents = new EnumMap<>(ReviewDirection.class);
		parts.forEach((direction, part) -> contents.put(direction, content(prefixOf(direction), direction, true,
			part.ratingOverall(), null, null, null, part.criteria(), part.wouldRecommend(), part.body(), part.pros(),
			part.cons(), tenancy.getProperty().getUnitLabel(), errors)));
		ReviewFormRequest.Part tenantPart = request.tenant();
		if (tenantPart != null) {
			for (String field : List.of("pros", "cons")) {
				String text = field.equals("pros") ? tenantPart.pros() : tenantPart.cons();
				if (!ReviewContentRules.strip(text).isEmpty()) {
					errors.add(new ApiProblem.FieldError("tenant." + field, "not_allowed",
						"Pros and cons are about a home. Write about the tenant in the comment."));
				}
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		boolean admin = caller.hasRole(Role.ADMIN);
		Map<ReviewDirection, ReviewView> written = new EnumMap<>(ReviewDirection.class);
		contents.forEach((direction, content) -> written.put(direction,
			view(write(tenancy, me, direction, content), me, admin)));
		return new ReviewFormView(written.get(ReviewDirection.TENANT_TO_PROPERTY),
			written.get(ReviewDirection.TENANT_TO_LANDLORD), written.get(ReviewDirection.LANDLORD_TO_TENANT));
	}

	/** The author row, locked: it serialises one author's submits (volume guard). */
	private User lockAuthor(AuthenticatedUser caller) {
		return users.lockByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(ReviewService::unauthenticated);
	}

	/** The tenancy row, locked: it serialises the reveal (D-46). */
	private Tenancy lockTenancy(UUID tenancyId, User me, AuthenticatedUser caller) {
		return tenancies.lockByPublicId(tenancyId)
			.filter(t -> sideOf(t, me) != null || caller.hasRole(Role.ADMIN))
			.orElseThrow(() -> ApiException.notFound("Tenancy"));
	}

	/** The trust rules of a new review in one direction: D-37, D-38, D-39, D-45 (account), D-91, D-139. */
	private void requireCanWrite(Tenancy tenancy, User me, ReviewDirection direction) {
		TenancyParty side = sideOf(tenancy, me);
		if (side == null || !DIRECTIONS.get(side).contains(direction)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "wrong_direction",
				"As the tenant you review the landlord and the home; as the landlord you review the tenant.");
		}
		if (!ReviewWindow.confirmed(tenancy)) {
			throw ApiException.rule("tenancy_not_confirmed", "Only a current or ended tenancy can be reviewed.");
		}
		if (direction == ReviewDirection.TENANT_TO_LANDLORD && UnclaimedLandlord.is(tenancy.getLandlord())) {
			throw ApiException.rule("landlord_not_on_platform",
				"This home's landlord is not on the platform yet, so only the home can be reviewed.");
		}
		approvals.require(me, roleOf(side));
		requireAccount(me);
		LocalDate today = today();
		if (today.isBefore(ReviewWindow.opensOn(tenancy))) {
			throw ApiException.rule("review_window_not_open",
				"Reviews for this tenancy open on " + ReviewWindow.opensOn(tenancy) + ".");
		}
		Optional<Review> existing = reviews.findByTenancyIdAndDirection(tenancy.getId(), direction);
		if (existing.isPresent()) {
			throw ApiException.conflict("review_exists", existing.get().getStatus() == ReviewStatus.WITHDRAWN
				? "You withdrew your review of this tenancy; it cannot be written again."
				: "You already reviewed this tenancy in this direction. Edit that review instead.");
		}
	}

	/** Saves a checked review and publishes what D-46 and D-47 allow now. */
	private Review write(Tenancy tenancy, User me, ReviewDirection direction, Content content) {
		Instant now = now();
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(direction);
		review.setAuthor(me);
		review.setSubjectUser(switch (direction) {
			case TENANT_TO_LANDLORD -> tenancy.getLandlord();
			case LANDLORD_TO_TENANT -> tenancy.getTenant();
			case TENANT_TO_PROPERTY -> null;
		});
		review.setProperty(tenancy.getProperty());
		apply(review, content);
		review.setSubmittedAt(now);
		review.setEditUntil(now.plus(EDIT_WINDOW));

		List<Review> published = new ArrayList<>();
		if (direction == ReviewDirection.TENANT_TO_PROPERTY) {
			published.add(review);
		} else {
			Optional<Review> counter = reviews.findByTenancyIdAndDirection(tenancy.getId(), counterOf(direction));
			// The counter-review reveals both (D-46). One already published (its 14 days ran out) or withdrawn
			// leaves nothing to protect, so this one publishes at once too.
			if (counter.isPresent()) {
				published.add(review);
				if (counter.get().getStatus() == ReviewStatus.AWAITING_REVEAL) {
					published.add(counter.get());
				}
			}
		}
		review.setStatus(ReviewStatus.AWAITING_REVEAL);
		reviews.saveAndFlush(review);
		published.forEach(r -> publish(r, now));
		return review;
	}

	/**
	 * {@code PATCH /reviews/{id}}: the author corrects their review at any time while it is awaiting reveal or published
	 * (D-40, D-41, changed by D-156). The edit takes the team's tick away, so the review is back in the admin queue,
	 * and the ratings are recomputed.
	 */
	@Transactional
	public ReviewView update(UUID reviewId, UpdateReviewRequest request, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Review review = writable(reviewId, me, caller);
		requireAuthor(review, me);
		if (!EDITABLE.contains(review.getStatus())) {
			throw ApiException.conflict("invalid_transition", "A " + review.getStatus() + " review cannot be edited.");
		}
		approvals.require(me, authorRole(review));
		Instant now = now();
		// D-157: the form fields keep their value; a form review keeps its optional comment.
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		Content content = content("", review.getDirection(), review.isFormReview(), request.ratingOverall(),
			request.ratingA(), request.ratingB(), request.ratingC(), names(review.getCriteria()),
			review.getWouldRecommend(), request.body(), review.getPros(), review.getCons(),
			review.getProperty().getUnitLabel(), errors);
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		EditHistory.Changes changes = new EditHistory.Changes()
			.field("ratingOverall", review.getRatingOverall(), content.ratingOverall())
			.field("ratingA", review.getRatingA(), content.ratingA())
			.field("ratingB", review.getRatingB(), content.ratingB())
			.field("ratingC", review.getRatingC(), content.ratingC())
			.field("criteria", criteriaText(review.getCriteria()), criteriaText(content.criteria()))
			.field("wouldRecommend", review.getWouldRecommend(), content.wouldRecommend())
			.field("body", review.getBody(), content.body())
			.field("pros", review.getPros(), content.pros())
			.field("cons", review.getCons(), content.cons());
		if (changes.isEmpty()) {
			return view(review, me, caller.hasRole(Role.ADMIN));
		}

		ReviewRevision previous = new ReviewRevision();
		previous.setReview(review);
		previous.setRevisionNo(revisions.findByReviewIdOrderByRevisionNoAsc(review.getId()).size() + 1);
		previous.setRatingOverall(review.getRatingOverall());
		previous.setRatingA(review.getRatingA());
		previous.setRatingB(review.getRatingB());
		previous.setRatingC(review.getRatingC());
		previous.setBody(review.getBody());
		previous.setCriteria(criteriaText(review.getCriteria()));
		previous.setWouldRecommend(review.getWouldRecommend());
		previous.setPros(review.getPros());
		previous.setCons(review.getCons());
		revisions.save(previous);

		apply(review, content);
		review.setLastEditedAt(now);
		// D-134, D-156: an edit of the text or ratings takes the team's decision away; the review is back in the queue.
		boolean wasVerified = unverify(review);
		history.edited(me, EditTarget.REVIEW, review.getId(), review.getPublicId(), wasVerified, changes);
		reviews.saveAndFlush(review);
		recomputer.reviewChanged(review);
		return view(review, me, caller.hasRole(Role.ADMIN));
	}

	/** {@code DELETE /reviews/{id}}: the author withdraws, at any time and without approval (D-41, D-91). */
	@Transactional
	public void withdraw(UUID reviewId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Review review = writable(reviewId, me, caller);
		requireAuthor(review, me);
		Instant now = now();
		boolean wasVerified = VerificationState.of(review.getVerificationStatus()) == VerificationState.VERIFIED;
		review.setStatus(ReviewStatus.WITHDRAWN);
		review.setDeletedAt(now);
		reviews.saveAndFlush(review);
		recomputer.reviewChanged(review);
		history.deleted(me, EditTarget.REVIEW, review.getId(), review.getPublicId(), wasVerified, "review",
			review.getRatingOverall() + " ★");
	}

	/** A ticked or rejected review reads {@code UNVERIFIED} again (D-134); returns whether it had the tick. */
	private static boolean unverify(Review review) {
		VerificationState state = VerificationState.of(review.getVerificationStatus());
		if (state != VerificationState.UNVERIFIED) {
			review.setVerificationStatus(VerificationState.UNVERIFIED);
			review.setVerificationDecidedBy(null);
			review.setVerificationDecidedAt(null);
			review.setVerificationReason(null);
		}
		return state == VerificationState.VERIFIED;
	}

	/** {@code POST /reviews/{id}/response}: the one public reply of the subject or the home's landlord (§5.7). */
	@Transactional
	public ReviewView respond(UUID reviewId, String text, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Review review = writable(reviewId, me, caller);
		requireResponder(review, me);
		approvals.require(me, responderRole(review));
		if (responses.findByReviewId(review.getId()).isPresent()) {
			throw ApiException.conflict("response_exists", "This review already has your response. Edit it instead.");
		}
		String body = ReviewContentRules.response(text, review.getProperty().getUnitLabel());
		Instant now = now();
		ReviewResponse response = new ReviewResponse();
		response.setReview(review);
		response.setAuthor(me);
		response.setBody(body);
		response.setEditUntil(now.plus(EDIT_WINDOW));
		responses.saveAndFlush(response);
		return view(review, me, caller.hasRole(Role.ADMIN));
	}

	/** {@code PATCH /reviews/{id}/response}: the responder edits within 7 days of posting. */
	@Transactional
	public ReviewView updateResponse(UUID reviewId, String text, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Review review = writable(reviewId, me, caller);
		requireResponder(review, me);
		ReviewResponse response = responses.findByReviewId(review.getId())
			.filter(r -> r.getStatus() == ResponseStatus.PUBLISHED)
			.orElseThrow(() -> ApiException.notFound("Response"));
		approvals.require(me, responderRole(review));
		if (now().isAfter(response.getEditUntil())) {
			throw ApiException.rule("edit_window_closed", "Responses can be edited for 7 days after they are posted.");
		}
		response.setBody(ReviewContentRules.response(text, review.getProperty().getUnitLabel()));
		responses.saveAndFlush(response);
		return view(review, me, caller.hasRole(Role.ADMIN));
	}

	/**
	 * Reveal job (D-46): person reviews hidden for 14 days publish alone.
	 *
	 * @return the number of reviews published
	 */
	@Transactional
	public int revealDue() {
		Instant now = now();
		List<UUID> due = reviews.findRevealDue(ReviewStatus.AWAITING_REVEAL, now.minus(REVEAL_AFTER));
		int published = 0;
		for (UUID id : due) {
			Review review = reviews.lockByPublicId(id).orElse(null);
			if (review != null && review.getStatus() == ReviewStatus.AWAITING_REVEAL) {
				publish(review, now);
				published++;
			}
		}
		return published;
	}

	// ---------------------------------------------------------------- reads

	/** {@code GET /reviews/{id}}: visibility follows the review's subject (§10). */
	public ReviewView get(UUID reviewId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		boolean admin = caller.hasRole(Role.ADMIN);
		Review review = reviews.findByPublicId(reviewId)
			.filter(r -> visible(r, me, admin))
			.orElseThrow(() -> ApiException.notFound("Review"));
		return view(review, me, admin);
	}

	/**
	 * {@code GET /properties/{id}/reviews}: published property reviews with full text and ratings, for everyone,
	 * signed in or not (D-161). An anonymous caller gets no actions; the author gets edit and withdraw.
	 */
	public PageResponse<ReviewView> forProperty(UUID propertyId, AuthenticatedUser caller, Pageable pageable) {
		User me = caller == null ? null : currentUser(caller);
		boolean admin = caller != null && caller.hasRole(Role.ADMIN);
		Property property = visibleProperty(propertyId, caller);
		Page<Review> page = reviews.findByPropertyIdAndDirectionAndStatus(property.getId(),
			ReviewDirection.TENANT_TO_PROPERTY, ReviewStatus.PUBLISHED, unordered(pageable));
		return views(page, me, admin);
	}

	/**
	 * {@code GET /properties/{id}/rating}: the stored aggregate, public like the property card (D-56), with the review
	 * form's per-parameter averages (D-158).
	 */
	public RatingDetail propertyRating(UUID propertyId, AuthenticatedUser caller) {
		Property property = visibleProperty(propertyId, caller);
		return breakdowns.forProperty(RatingDetail.of(aggregates.findById(
			new RatingAggregateId(RatingSubjectType.PROPERTY, property.getId())).orElse(null)), property.getId());
	}

	/**
	 * Published reviews about {@code subject} in one person direction. The caller checks that the viewer may see
	 * that profile (D-54, D-55).
	 */
	public PageResponse<ReviewView> about(User subject, ReviewDirection direction, User viewer, boolean admin,
		Pageable pageable) {
		Page<Review> page = reviews.findBySubjectUserIdAndDirectionAndStatus(subject.getId(), direction,
			ReviewStatus.PUBLISHED, unordered(pageable));
		return views(page, viewer, admin);
	}

	/**
	 * {@code GET /me/reviews/written?as=}: the caller's own reviews in every state except withdrawn; {@code as} keeps
	 * the ones written from that side ({@code TENANT}: of homes and landlords, {@code LANDLORD}: of tenants).
	 */
	public PageResponse<ReviewView> written(AuthenticatedUser caller, TenancyParty as, Pageable pageable) {
		User me = currentUser(caller);
		Set<ReviewStatus> visible = EnumSet.of(ReviewStatus.AWAITING_REVEAL, ReviewStatus.PUBLISHED, ReviewStatus.HIDDEN);
		Page<Review> page = as == null
			? reviews.findByAuthorIdAndStatusIn(me.getId(), visible, pageable)
			: reviews.findByAuthorIdAndDirectionInAndStatusIn(me.getId(), DIRECTIONS.get(as), visible, pageable);
		return views(page, me, caller.hasRole(Role.ADMIN));
	}

	/**
	 * {@code GET /me/reviews/received?as=LANDLORD|TENANT}: published reviews about the caller, plus the ones still
	 * awaiting reveal as bare "pending" entries (D-46), so they know a review waits for theirs. Ordered by the random
	 * review id only (D-169): posting order would tell the subject which ex-tenant wrote which, and rating order would
	 * place a pending review's hidden rating among the published ones.
	 */
	public PageResponse<ReviewView> received(AuthenticatedUser caller, TenancyParty as, Pageable pageable) {
		User me = currentUser(caller);
		Collection<ReviewDirection> directions = as == null
			? EnumSet.of(ReviewDirection.TENANT_TO_LANDLORD, ReviewDirection.LANDLORD_TO_TENANT)
			: EnumSet.of(as == TenancyParty.LANDLORD ? ReviewDirection.TENANT_TO_LANDLORD
			: ReviewDirection.LANDLORD_TO_TENANT);
		Page<Review> page = reviews.findBySubjectUserIdAndDirectionInAndStatusIn(me.getId(), directions,
			EnumSet.of(ReviewStatus.PUBLISHED, ReviewStatus.AWAITING_REVEAL), unordered(pageable));
		boolean admin = caller.hasRole(Role.ADMIN);
		Map<Long, ReviewResponse> replies = responsesOf(page.getContent());
		return PageResponse.of(page, r -> r.getStatus() == ReviewStatus.AWAITING_REVEAL
			? pending(r) : view(r, me, admin, replies.get(r.getId())));
	}

	/** {@code GET /me/review-tasks}: reviews the caller can write now, the oldest tenancy first. */
	public List<ReviewTask> tasks(AuthenticatedUser caller) {
		User me = currentUser(caller);
		LocalDate today = today();
		List<Tenancy> mine = new ArrayList<>(tenancies.findByLandlordIdAndStatusIn(me.getId(), CONFIRMED));
		mine.addAll(tenancies.findByTenantIdAndStatusIn(me.getId(), CONFIRMED));
		List<ReviewTask> tasks = new ArrayList<>();
		for (Tenancy tenancy : mine) {
			TenancyParty side = sideOf(tenancy, me);
			if (side == null || !ReviewWindow.isOpen(tenancy, today)) {
				continue;
			}
			Map<ReviewDirection, Review> written = reviews.findByTenancyId(tenancy.getId()).stream()
				.collect(Collectors.toMap(Review::getDirection, Function.identity()));
			for (ReviewDirection direction : DIRECTIONS.get(side)) {
				if (written.containsKey(direction) || direction == ReviewDirection.TENANT_TO_LANDLORD
					&& UnclaimedLandlord.is(tenancy.getLandlord())) {
					continue;
				}
				Review counter = direction == ReviewDirection.TENANT_TO_PROPERTY ? null : written.get(counterOf(direction));
				User counterparty = switch (direction) {
					case TENANT_TO_LANDLORD -> tenancy.getLandlord();
					case LANDLORD_TO_TENANT -> tenancy.getTenant();
					case TENANT_TO_PROPERTY -> null;
				};
				// A tenancy with no linked tenant (tenant_id NULL, landlord-adds-tenant LT-5) has no one to name yet.
				PartyRef ref = counterparty != null ? party(counterparty)
					: direction == ReviewDirection.LANDLORD_TO_TENANT ? new PartyRef(null, YOUR_TENANT) : null;
				tasks.add(new ReviewTask(tenancy.getPublicId(), direction, propertyRef(tenancy.getProperty()), ref,
					period(tenancy),
					counter != null && counter.getStatus() == ReviewStatus.AWAITING_REVEAL));
			}
		}
		tasks.sort(Comparator.comparing(t -> t.period().from()));
		return tasks;
	}

	// -------------------------------------------------------------- helpers
	
	/**
	 * A review list's order (D-169) must not give away who posted when: equal ratings, or every review of an unsorted
	 * list, are ordered by the random review id, never by the row or posting order.
	 */
	static Pageable unordered(Pageable pageable) {
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
			pageable.getSort().and(Sort.by(Sort.Direction.ASC, "publicId")));
	}

	private void publish(Review review, Instant now) {
		review.setStatus(ReviewStatus.PUBLISHED);
		review.setPublishedAt(now);
		reviews.saveAndFlush(review);
		recomputer.reviewChanged(review);
		notifier.published(review);
	}

	/** Who may read a review (§10): author and admins always (except withdrawn); others only once published. */
	private boolean visible(Review review, User me, boolean admin) {
		if (admin) {
			return true;
		}
		if (review.getStatus() == ReviewStatus.WITHDRAWN) {
			return false;
		}
		if (isAuthor(review, me)) {
			return true;
		}
		if (review.getStatus() != ReviewStatus.PUBLISHED) {
			return false;
		}
		return review.getDirection() != ReviewDirection.LANDLORD_TO_TENANT
			|| review.getSubjectUser() != null && access.canSeeTenant(me, review.getSubjectUser(), false);
	}

	/** {@link #visible} with the row locked, for a write. */
	private Review writable(UUID reviewId, User me, AuthenticatedUser caller) {
		return reviews.lockByPublicId(reviewId)
			.filter(r -> visible(r, me, caller.hasRole(Role.ADMIN)))
			.orElseThrow(() -> ApiException.notFound("Review"));
	}

	private Property visibleProperty(UUID propertyId, AuthenticatedUser caller) {
		return properties.findByPublicIdAndDeletedAtIsNull(propertyId)
			.filter(p -> p.getStatus().isPublic() || caller != null
				&& (p.getLandlord().getPublicId().equals(caller.publicId()) || caller.hasRole(Role.ADMIN)))
			.orElseThrow(() -> ApiException.notFound("Property"));
	}

	/** D-41, changed by D-156: the author may edit a review that awaits reveal or is published. */
	private static boolean canEdit(Review review) {
		return EDITABLE.contains(review.getStatus());
	}

	/** D-45: email verified, and a first review needs an account at least 7 days old. */
	private void requireAccount(User me) {
		if (me.getEmailVerifiedAt() == null) {
			throw ApiException.rule("email_not_verified", "Your email address is not verified.");
		}
	}

	/** D-45: at most {@value #MAX_PER_DAY} reviews per rolling 24 hours, {@code adding} of them now. */
	private void requireVolume(User me, int adding) {
		Instant now = now();
		List<Review> lastDay = reviews.findByAuthorIdAndSubmittedAtAfterOrderBySubmittedAtAsc(me.getId(),
			now.minus(Duration.ofDays(1)));
		if (lastDay.size() + adding > MAX_PER_DAY) {
			// The oldest reviews must leave the window until all new ones fit.
			Instant frees = lastDay.get(lastDay.size() + adding - 1 - MAX_PER_DAY).getSubmittedAt()
				.plus(Duration.ofDays(1));
			throw ApiException.rateLimited(Duration.between(now, frees));
		}
	}

	/** A review's checked ratings and text (D-27, D-43, D-157). */
	private record Content(boolean form, int ratingOverall, Integer ratingA, Integer ratingB, Integer ratingC,
		Map<ReviewCriterion, Integer> criteria, Boolean wouldRecommend, String body, String pros, String cons) {
	}

	/**
	 * Checks one review's ratings and text, adding a field error under {@code prefix} for each broken rule. The
	 * overall rating is required; parameter ratings are optional, 1–5, and must belong to the direction. A form review
	 * ({@code form}) has an optional comment; any other review's body is the D-43 review text
	 * of 30 characters or more. Pros and cons are optional (D-157).
	 */
	private static Content content(String prefix, ReviewDirection direction, boolean form, Integer overall,
		Integer ratingA, Integer ratingB, Integer ratingC, Map<String, Integer> rawCriteria, Boolean wouldRecommend,
		String body, String pros, String cons, String unitLabel, List<ApiProblem.FieldError> errors) {
		if (overall == null) {
			errors.add(new ApiProblem.FieldError(prefix + "ratingOverall", "not_null",
				"Pick an overall rating from 1 to 5 stars."));
		} else if (overall < 1 || overall > 5) {
			errors.add(new ApiProblem.FieldError(prefix + "ratingOverall", "out_of_range", "Pick 1 to 5 stars."));
		}
		Map<ReviewCriterion, Integer> criteria = new EnumMap<>(ReviewCriterion.class);
		if (rawCriteria != null) {
			rawCriteria.forEach((name, rating) -> {
				String field = prefix + "criteria." + name;
				ReviewCriterion criterion = criterionOf(name);
				if (criterion == null) {
					errors.add(new ApiProblem.FieldError(field, "unknown_criterion", "This is not a question of the form."));
				} else if (criterion.direction() != direction) {
					errors.add(new ApiProblem.FieldError(field, "wrong_direction",
						"This question belongs to the other part of the form."));
				} else if (rating != null && (rating < 1 || rating > 5)) {
					errors.add(new ApiProblem.FieldError(field, "out_of_range", "Pick 1 to 5 stars."));
				} else if (rating != null) {
					criteria.put(criterion, rating);
				}
			});
		}
		errors.addAll(ReviewContentRules.problems(prefix + "body", body, form ? 0 : ReviewContentRules.REVIEW_MIN,
			ReviewContentRules.REVIEW_MAX, unitLabel));
		errors.addAll(ReviewContentRules.problems(prefix + "pros", pros, 0, ReviewContentRules.PROS_CONS_MAX, unitLabel));
		errors.addAll(ReviewContentRules.problems(prefix + "cons", cons, 0, ReviewContentRules.PROS_CONS_MAX, unitLabel));
		return new Content(form, overall == null ? 0 : overall, ratingA, ratingB, ratingC, criteria, wouldRecommend,
			ReviewContentRules.strip(body), blankToNull(pros), blankToNull(cons));
	}

	private static void apply(Review review, Content content) {
		review.setFormReview(content.form());
		review.setRatingOverall(content.ratingOverall());
		review.setRatingA(content.ratingA());
		review.setRatingB(content.ratingB());
		review.setRatingC(content.ratingC());
		review.getCriteria().clear();
		review.getCriteria().putAll(content.criteria());
		review.setWouldRecommend(content.wouldRecommend());
		review.setBody(content.body());
		review.setPros(content.pros());
		review.setCons(content.cons());
	}

	private static ReviewCriterion criterionOf(String name) {
		try {
			return name == null ? null : ReviewCriterion.valueOf(name);
		} catch (IllegalArgumentException unknown) {
			return null;
		}
	}

	private static Map<String, Integer> names(Map<ReviewCriterion, Integer> criteria) {
		Map<String, Integer> names = new LinkedHashMap<>();
		criteria.forEach((criterion, rating) -> names.put(criterion.name(), rating));
		return names;
	}

	private static String blankToNull(String text) {
		String stripped = ReviewContentRules.strip(text);
		return stripped.isEmpty() ? null : stripped;
	}

	/** The parameter ratings in form order. */
	private static Map<ReviewCriterion, Integer> criteria(Review review) {
		Map<ReviewCriterion, Integer> ordered = new LinkedHashMap<>();
		for (ReviewCriterion criterion : ReviewCriterion.values()) {
			Integer rating = review.getCriteria().get(criterion);
			if (rating != null) {
				ordered.put(criterion, rating);
			}
		}
		return ordered;
	}

	/** "WATER_SUPPLY=4,NOISE=2" in form order (revisions, edit history); null when none were rated. */
	private static String criteriaText(Map<ReviewCriterion, Integer> criteria) {
		if (criteria.isEmpty()) {
			return null;
		}
		return Arrays.stream(ReviewCriterion.values()).filter(criteria::containsKey)
			.map(c -> c + "=" + criteria.get(c)).collect(Collectors.joining(","));
	}

	private static String prefixOf(ReviewDirection direction) {
		return switch (direction) {
			case TENANT_TO_PROPERTY -> "home.";
			case TENANT_TO_LANDLORD -> "landlord.";
			case LANDLORD_TO_TENANT -> "tenant.";
		};
	}

	private static void requireAuthor(Review review, User me) {
		if (!isAuthor(review, me)) {
			// D-42: the subject can never edit, withdraw or hide a review about them.
			throw new ApiException(HttpStatus.FORBIDDEN, "not_author", "Only the author can change this review.");
		}
	}

	private static void requireResponder(Review review, User me) {
		if (review.getStatus() != ReviewStatus.PUBLISHED) {
			throw ApiException.conflict("invalid_transition", "Only a published review can get a response.");
		}
		if (!isResponder(review, me)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_subject",
				"Only the person reviewed, or the landlord of the home, can respond.");
		}
	}

	/** {@code me} is null for an anonymous reader (D-161). */
	private static boolean isAuthor(Review review, User me) {
		return me != null && review.getAuthor().getId().equals(me.getId());
	}

	/** The subject of a person review, or the home's landlord for a property review (§5.7). */
	private static boolean isResponder(Review review, User me) {
		User responder = review.getDirection() == ReviewDirection.TENANT_TO_PROPERTY
			? review.getProperty().getLandlord() : review.getSubjectUser();
		return me != null && responder != null && responder.getId().equals(me.getId());
	}

	private static Role authorRole(Review review) {
		return review.getDirection() == ReviewDirection.LANDLORD_TO_TENANT ? Role.LANDLORD : Role.TENANT;
	}

	private static Role responderRole(Review review) {
		return review.getDirection() == ReviewDirection.LANDLORD_TO_TENANT ? Role.TENANT : Role.LANDLORD;
	}

	private static Role roleOf(TenancyParty side) {
		return side == TenancyParty.LANDLORD ? Role.LANDLORD : Role.TENANT;
	}

	static ReviewDirection counterOf(ReviewDirection direction) {
		return direction == ReviewDirection.TENANT_TO_LANDLORD
			? ReviewDirection.LANDLORD_TO_TENANT : ReviewDirection.TENANT_TO_LANDLORD;
	}

	private static TenancyParty sideOf(Tenancy tenancy, User user) {
		if (tenancy.getLandlord().getId().equals(user.getId())) {
			return TenancyParty.LANDLORD;
		}
		if (tenancy.getTenant() != null && tenancy.getTenant().getId().equals(user.getId())) {
			return TenancyParty.TENANT;
		}
		return null;
	}

	private User currentUser(AuthenticatedUser caller) {
		return users.findByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(ReviewService::unauthenticated);
	}

	private static ApiException unauthenticated() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue.");
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}

	// ----------------------------------------------------------------- views

	private PageResponse<ReviewView> views(Page<Review> page, User me, boolean admin) {
		Map<Long, ReviewResponse> replies = responsesOf(page.getContent());
		return PageResponse.of(page, r -> view(r, me, admin, replies.get(r.getId())));
	}

	private Map<Long, ReviewResponse> responsesOf(List<Review> list) {
		Map<Long, ReviewResponse> map = new HashMap<>();
		for (Review r : list) {
			responses.findByReviewId(r.getId()).ifPresent(response -> map.put(r.getId(), response));
		}
		return map;
	}

	ReviewView view(Review review, User me, boolean admin) {
		return view(review, me, admin, responses.findByReviewId(review.getId()).orElse(null));
	}

	private ReviewView view(Review r, User me, boolean admin, ReviewResponse reply) {
		boolean author = isAuthor(r, me);
		boolean responder = isResponder(r, me);
		boolean textHidden = r.getTextHiddenAt() != null;
		boolean privileged = author || admin;
		ReviewView.Response response = null;
		if (reply != null && (reply.getStatus() == ResponseStatus.PUBLISHED || responder || admin)) {
			User replier = reply.getAuthor();
			response = new ReviewView.Response(reply.getBody(), label(r, replier),
				isSelf(replier, me) || admin ? PublicNames.of(replier) : null,
				responder || admin ? reply.getCreatedAt() : null, responder || admin ? reply.getEditUntil() : null);
		}
		boolean published = r.getStatus() == ReviewStatus.PUBLISHED;
		ReviewView.Actions actions = new ReviewView.Actions(
			author && canEdit(r),
			author && r.getStatus() != ReviewStatus.WITHDRAWN,
			responder && published && reply == null,
			responder && published && reply != null && reply.getStatus() == ResponseStatus.PUBLISHED
				&& !now().isAfter(reply.getEditUntil()));
		return new ReviewView(
			r.getPublicId(),
			r.getDirection(),
			r.getStatus(),
			r.getRatingOverall(),
			r.getRatingA(),
			r.getRatingB(),
			r.getRatingC(),
			r.isFormReview() ? Boolean.TRUE : null,
			r.getCriteria().isEmpty() ? null : criteria(r),
			r.getWouldRecommend(),
			textHidden && !privileged ? null : blankToNull(r.getBody()),
			textHidden && !privileged ? null : r.getPros(),
			textHidden && !privileged ? null : r.getCons(),
			textHidden ? Boolean.TRUE : null,
			authorLine(r, me, admin),
			r.getSubjectUser() == null ? null : person(r, r.getSubjectUser(), me, admin),
			propertyRef(r.getProperty()),
			// D-169: the stay and the exact times only for the author and admins; others get the year, the month only
			// for a stay that had ended by then (D-171), and "edited".
			privileged ? period(r.getTenancy()) : null,
			publishedMonth(r),
			r.getPublishedAt() == null ? null : month(r.getPublishedAt()).getYear(),
			r.getLastEditedAt() != null ? Boolean.TRUE : null,
			privileged ? r.getSubmittedAt() : null,
			privileged ? r.getPublishedAt() : null,
			privileged ? r.getLastEditedAt() : null,
			privileged ? r.getEditUntil() : null,
			privileged ? r.getHiddenReason() : null,
			response,
			actions,
			VerificationState.of(r.getVerificationStatus()));
	}

	/**
	 * A review about the caller that still awaits reveal: no ratings, text or author (D-46), and no stay, which would
	 * name the tenancy and so the author (D-169).
	 */
	private ReviewView pending(Review r) {
		return new ReviewView(r.getPublicId(), r.getDirection(), r.getStatus(), null, null, null, null, null, null,
			null, null, null, null, null, null, null, propertyRef(r.getProperty()), null, null, null, null, null, null,
			null, null, null, null, ReviewView.Actions.NONE, null);
	}

	/**
	 * The author line (D-168, D-169): an anonymous label with no year, the coarse stay length of a tenant author
	 * (D-171) and the three badges, the name only for the author and admins, the id for admins only. "Agreement
	 * verified" comes from this review's tenancy.
	 */
	private ReviewView.Author authorLine(Review r, User me, boolean admin) {
		User author = r.getAuthor();
		if (deleted(author)) {
			return new ReviewView.Author(null, PublicNames.DELETED_USER, null, null, TrustBadges.NONE);
		}
		Tenancy tenancy = r.getTenancy();
		StayLength stayLength = isTenant(tenancy, author) && r.getSubmittedAt() != null ? PublicLabels.stayLength(
			tenancy.getStartDate(), tenancy.getEndDate(), LocalDate.ofInstant(r.getSubmittedAt(), ZoneOffset.UTC)) : null;
		return new ReviewView.Author(admin ? author.getPublicId() : null, label(r, author), stayLength,
			isSelf(author, me) || admin ? PublicNames.of(author) : null,
			identity.trustBadges(author, r.getTenancy().getEvidenceLevel() == EvidenceLevel.DOCUMENT));
	}

	/** The reviewed person (D-168): label for everyone, the name only for that person and admins. */
	private static ReviewView.Person person(Review r, User user, User me, boolean admin) {
		if (deleted(user)) {
			return new ReviewView.Person(null, PublicNames.DELETED_USER, null);
		}
		return new ReviewView.Person(user.getPublicId(), label(r, user),
			isSelf(user, me) || admin ? PublicNames.of(user) : null);
	}

	/**
	 * How {@code user} shows on {@code r} (D-168, D-169): the tenancy's tenant as "Verified tenant" or "Tenant", else
	 * "Owner"; never a year.
	 */
	private static String label(Review r, User user) {
		if (deleted(user)) {
			return PublicNames.DELETED_USER;
		}
		Tenancy tenancy = r.getTenancy();
		return isTenant(tenancy, user) ? PublicLabels.tenant(verifiedStay(tenancy)) : PublicLabels.OWNER;
	}
	
	private static boolean isTenant(Tenancy tenancy, User user) {
		return tenancy.getTenant() != null && tenancy.getTenant().getId().equals(user.getId());
	}
	
	/**
	 * "Verified tenant" (D-169): the landlord confirmed the stay or started it (D-142), a rental agreement backs it, or
	 * the team verified it. A tenant's one-sided record, such as a home whose landlord is not on the platform, is
	 * plain "Tenant".
	 */
	static boolean verifiedStay(Tenancy tenancy) {
		return TenancyService.mutual(tenancy) || tenancy.getEvidenceLevel() == EvidenceLevel.DOCUMENT
			|| tenancy.getVerificationStatus() == VerificationState.VERIFIED;
	}
	
	/**
	 * The month a review was published (D-171), only when its stay had ended by that day: a month next to a current
	 * stay would tell the landlord that this tenant wrote it. Otherwise only the year goes out.
	 */
	static YearMonth publishedMonth(Review r) {
		Instant at = r.getPublishedAt();
		LocalDate end = r.getTenancy().getEndDate();
		return at == null || end == null || end.isAfter(LocalDate.ofInstant(at, ZoneOffset.UTC)) ? null : month(at);
	}

	/** Month and year in UTC, never the day or time (D-169); null for null. */
	static YearMonth month(Instant at) {
		return at == null ? null : YearMonth.from(at.atZone(ZoneOffset.UTC));
	}

	private static boolean isSelf(User user, User me) {
		return me != null && user.getId().equals(me.getId());
	}

	private static boolean deleted(User user) {
		return user.getStatus() == UserStatus.DELETED || user.getDeletedAt() != null;
	}

	private static PartyRef party(User user) {
		return user.getDeletedAt() != null ? new PartyRef(null, PublicNames.DELETED_USER)
			: new PartyRef(user.getPublicId(), PublicNames.of(user));
	}

	static ReviewView.PropertyRef propertyRef(Property p) {
		return new ReviewView.PropertyRef(p.getPublicId(), p.getBuildingName(), p.getLocality(), CityRef.of(p.getCity()));
	}

	/** Month and year only (D-57). */
	static ReviewView.Period period(Tenancy t) {
		return new ReviewView.Period(YearMonth.from(t.getStartDate()),
			t.getEndDate() == null ? null : YearMonth.from(t.getEndDate()));
	}

}
