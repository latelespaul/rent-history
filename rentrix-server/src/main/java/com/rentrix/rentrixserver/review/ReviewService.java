package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.identity.Badges;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.rating.RatingDetail;
import com.rentrix.rentrixserver.rating.RatingRecomputer;
import com.rentrix.rentrixserver.review.dto.CreateReviewRequest;
import com.rentrix.rentrixserver.review.dto.ReviewTask;
import com.rentrix.rentrixserver.review.dto.ReviewView;
import com.rentrix.rentrixserver.review.dto.UpdateReviewRequest;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.tenancy.dto.PartyRef;
import com.rentrix.rentrixserver.user.PublicNames;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.profile.ProfileAccess;
import com.rentrix.rentrixserver.verification.ApprovalGuard;
import com.rentrix.rentrixserver.verification.GreenTicks;
import com.rentrix.rentrixserver.verification.VerificationState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
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
	private static final String FORMER_USER = "Former member";

	private final ReviewRepository reviews;
	private final ReviewRevisionRepository revisions;
	private final ReviewResponseRepository responses;
	private final RatingAggregateRepository aggregates;
	private final TenancyRepository tenancies;
	private final PropertyRepository properties;
	private final UserRepository users;
	private final ApprovalGuard approvals;
	private final IdentityService identity;
	private final GreenTicks greenTicks;
	private final ProfileAccess access;
	private final RatingRecomputer recomputer;
	private final ReviewNotifier notifier;
	private final Clock clock;

	// --------------------------------------------------------------- writes

	/** {@code POST /tenancies/{id}/reviews}. */
	@Transactional
	public ReviewView create(UUID tenancyId, CreateReviewRequest request, AuthenticatedUser caller) {
		// The user row lock serialises one author's submits (volume guard); the tenancy lock serialises the reveal.
		User me = users.lockByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(ReviewService::unauthenticated);
		Tenancy tenancy = tenancies.lockByPublicId(tenancyId)
			.filter(t -> sideOf(t, me) != null || caller.hasRole(Role.ADMIN))
			.orElseThrow(() -> ApiException.notFound("Tenancy"));
		TenancyParty side = sideOf(tenancy, me);
		ReviewDirection direction = request.direction();
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
		requireVolume(me);
		String body = ReviewContentRules.review(request.body(), tenancy.getProperty().getUnitLabel());

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
		review.setRatingOverall(request.ratingOverall());
		review.setRatingA(request.ratingA());
		review.setRatingB(request.ratingB());
		review.setRatingC(request.ratingC());
		review.setBody(body);
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
		return view(review, me, caller.hasRole(Role.ADMIN));
	}

	/**
	 * {@code PATCH /reviews/{id}}: the author of a review still {@code AWAITING_REVEAL}, inside the edit window or with
	 * the one post-end update (D-40, D-41). A published review is frozen.
	 */
	@Transactional
	public ReviewView update(UUID reviewId, UpdateReviewRequest request, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Review review = writable(reviewId, me, caller);
		requireAuthor(review, me);
		if (review.getStatus() == ReviewStatus.PUBLISHED) {
			throw ApiException.rule("edit_window_closed", "A review is frozen once it is published.");
		}
		if (review.getStatus() != ReviewStatus.AWAITING_REVEAL) {
			throw ApiException.conflict("invalid_transition", "A " + review.getStatus() + " review cannot be edited.");
		}
		approvals.require(me, authorRole(review));
		Instant now = now();
		boolean inWindow = !now.isAfter(review.getEditUntil());
		boolean postEndUpdate = !inWindow && postEndUpdateAllowed(review);
		if (!inWindow && !postEndUpdate) {
			throw ApiException.rule("edit_window_closed", review.isPostEndUpdateUsed()
				? "You already used the one update after the tenancy ended."
				: "Reviews can be edited for 7 days after they are written.");
		}
		String body = ReviewContentRules.review(request.body(), review.getProperty().getUnitLabel());

		ReviewRevision previous = new ReviewRevision();
		previous.setReview(review);
		previous.setRevisionNo(revisions.findByReviewIdOrderByRevisionNoAsc(review.getId()).size() + 1);
		previous.setRatingOverall(review.getRatingOverall());
		previous.setRatingA(review.getRatingA());
		previous.setRatingB(review.getRatingB());
		previous.setRatingC(review.getRatingC());
		previous.setBody(review.getBody());
		revisions.save(previous);

		review.setRatingOverall(request.ratingOverall());
		review.setRatingA(request.ratingA());
		review.setRatingB(request.ratingB());
		review.setRatingC(request.ratingC());
		review.setBody(body);
		review.setLastEditedAt(now);
		if (postEndUpdate) {
			review.setPostEndUpdateUsed(true);
			review.setEditUntil(now.plus(EDIT_WINDOW));
		}
		reviews.saveAndFlush(review);
		return view(review, me, caller.hasRole(Role.ADMIN));
	}

	/** {@code DELETE /reviews/{id}}: the author withdraws, at any time and without approval (D-41, D-91). */
	@Transactional
	public void withdraw(UUID reviewId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Review review = writable(reviewId, me, caller);
		requireAuthor(review, me);
		Instant now = now();
		review.setStatus(ReviewStatus.WITHDRAWN);
		review.setDeletedAt(now);
		reviews.saveAndFlush(review);
		recomputer.reviewChanged(review);
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

	/** {@code GET /properties/{id}/reviews}: published property reviews, for signed-in readers (D-56). */
	public PageResponse<ReviewView> forProperty(UUID propertyId, AuthenticatedUser caller, Pageable pageable) {
		User me = currentUser(caller);
		boolean admin = caller.hasRole(Role.ADMIN);
		Property property = visibleProperty(propertyId, caller);
		Page<Review> page = reviews.findByPropertyIdAndDirectionAndStatus(property.getId(),
			ReviewDirection.TENANT_TO_PROPERTY, ReviewStatus.PUBLISHED, pageable);
		return views(page, me, admin);
	}

	/** {@code GET /properties/{id}/rating}: the stored aggregate, public like the property card (D-56). */
	public RatingDetail propertyRating(UUID propertyId, AuthenticatedUser caller) {
		Property property = visibleProperty(propertyId, caller);
		return RatingDetail.of(aggregates.findById(new RatingAggregateId(RatingSubjectType.PROPERTY, property.getId()))
			.orElse(null));
	}

	/**
	 * Published reviews about {@code subject} in one person direction. The caller checks that the viewer may see
	 * that profile (D-54, D-55).
	 */
	public PageResponse<ReviewView> about(User subject, ReviewDirection direction, User viewer, boolean admin,
		Pageable pageable) {
		Page<Review> page = reviews.findBySubjectUserIdAndDirectionAndStatus(subject.getId(), direction,
			ReviewStatus.PUBLISHED, pageable);
		return views(page, viewer, admin);
	}

	/** {@code GET /me/reviews/written}: the caller's own reviews in every state except withdrawn. */
	public PageResponse<ReviewView> written(AuthenticatedUser caller, Pageable pageable) {
		User me = currentUser(caller);
		Page<Review> page = reviews.findByAuthorIdAndStatusIn(me.getId(),
			EnumSet.of(ReviewStatus.AWAITING_REVEAL, ReviewStatus.PUBLISHED, ReviewStatus.HIDDEN), pageable);
		return views(page, me, caller.hasRole(Role.ADMIN));
	}

	/**
	 * {@code GET /me/reviews/received?as=LANDLORD|TENANT}: published reviews about the caller, plus the ones still
	 * awaiting reveal as bare "pending" entries (D-46), so they know a review waits for theirs.
	 */
	public PageResponse<ReviewView> received(AuthenticatedUser caller, TenancyParty as, Pageable pageable) {
		User me = currentUser(caller);
		Collection<ReviewDirection> directions = as == null
			? EnumSet.of(ReviewDirection.TENANT_TO_LANDLORD, ReviewDirection.LANDLORD_TO_TENANT)
			: EnumSet.of(as == TenancyParty.LANDLORD ? ReviewDirection.TENANT_TO_LANDLORD
			: ReviewDirection.LANDLORD_TO_TENANT);
		Page<Review> page = reviews.findBySubjectUserIdAndDirectionInAndStatusIn(me.getId(), directions,
			EnumSet.of(ReviewStatus.PUBLISHED, ReviewStatus.AWAITING_REVEAL), pageable);
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
				tasks.add(new ReviewTask(tenancy.getPublicId(), direction, propertyRef(tenancy.getProperty()),
					counterparty == null ? null : party(counterparty), period(tenancy),
					counter != null && counter.getStatus() == ReviewStatus.AWAITING_REVEAL));
			}
		}
		tasks.sort(Comparator.comparing(t -> t.period().from()));
		return tasks;
	}

	// -------------------------------------------------------------- helpers

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
			|| access.canSeeTenant(me, review.getSubjectUser(), false);
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

	/** D-40: written while the tenancy was current, the tenancy has ended, and the one update unused. */
	private boolean postEndUpdateAllowed(Review review) {
		Tenancy tenancy = review.getTenancy();
		return !review.isPostEndUpdateUsed() && tenancy.getStatus() == TenancyStatus.ENDED
			&& tenancy.getEndedAt() != null && review.getSubmittedAt().isBefore(tenancy.getEndedAt());
	}

	/** D-41: only an unpublished review is editable, so neither side can react to a revealed review. */
	private boolean canEdit(Review review) {
		return review.getStatus() == ReviewStatus.AWAITING_REVEAL && (!now().isAfter(review.getEditUntil()) || postEndUpdateAllowed(review));
	}

	/** D-45: email verified, and a first review needs an account at least 7 days old. */
	private void requireAccount(User me) {
		if (me.getEmailVerifiedAt() == null) {
			throw ApiException.rule("email_not_verified", "Your email address is not verified.");
		}
	}

	/** D-45: at most {@value #MAX_PER_DAY} reviews per rolling 24 hours. */
	private void requireVolume(User me) {
		Instant now = now();
		List<Review> lastDay = reviews.findByAuthorIdAndSubmittedAtAfterOrderBySubmittedAtAsc(me.getId(),
			now.minus(Duration.ofDays(1)));
		if (lastDay.size() >= MAX_PER_DAY) {
			Instant frees = lastDay.get(lastDay.size() - MAX_PER_DAY).getSubmittedAt().plus(Duration.ofDays(1));
			throw ApiException.rateLimited(Duration.between(now, frees));
		}
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

	private static boolean isAuthor(Review review, User me) {
		return review.getAuthor().getId().equals(me.getId());
	}

	/** The subject of a person review, or the home's landlord for a property review (§5.7). */
	private static boolean isResponder(Review review, User me) {
		User responder = review.getDirection() == ReviewDirection.TENANT_TO_PROPERTY
			? review.getProperty().getLandlord() : review.getSubjectUser();
		return responder != null && responder.getId().equals(me.getId());
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
			response = new ReviewView.Response(reply.getBody(), displayName(reply.getAuthor()), reply.getCreatedAt(),
				responder || admin ? reply.getEditUntil() : null);
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
			textHidden && !privileged ? null : r.getBody(),
			textHidden ? Boolean.TRUE : null,
			authorLine(r, admin),
			r.getSubjectUser() == null ? null : party(r.getSubjectUser()),
			propertyRef(r.getProperty()),
			period(r.getTenancy()),
			privileged ? r.getSubmittedAt() : null,
			r.getPublishedAt(),
			r.getLastEditedAt(),
			privileged ? r.getEditUntil() : null,
			privileged ? r.getHiddenReason() : null,
			response,
			actions,
			VerificationState.of(r.getVerificationStatus()));
	}

	/** A review about the caller that still awaits reveal: no ratings, text or author (D-46). */
	private ReviewView pending(Review r) {
		return new ReviewView(r.getPublicId(), r.getDirection(), r.getStatus(), null, null, null, null, null, null,
			null, null, propertyRef(r.getProperty()), period(r.getTenancy()), null, null, null, null, null, null,
			ReviewView.Actions.NONE, null);
	}

	/** D-28 author line; the id only for landlord authors, whose profiles are open to signed-in users (D-55). */
	private ReviewView.Author authorLine(Review r, boolean admin) {
		User author = r.getAuthor();
		if (author.getDeletedAt() != null) {
			return new ReviewView.Author(null, FORMER_USER, Badges.NONE, false);
		}
		boolean landlordAuthor = r.getDirection() == ReviewDirection.LANDLORD_TO_TENANT;
		return new ReviewView.Author(landlordAuthor || admin ? author.getPublicId() : null,
			PublicNames.shortName(author.getDisplayName()), identity.badges(author),
			greenTicks.has(author, landlordAuthor ? Role.LANDLORD : Role.TENANT));
	}

	private static PartyRef party(User user) {
		return user.getDeletedAt() != null ? new PartyRef(null, FORMER_USER)
			: new PartyRef(user.getPublicId(), PublicNames.shortName(user.getDisplayName()));
	}

	private static String displayName(User user) {
		return user.getDeletedAt() != null ? FORMER_USER : PublicNames.shortName(user.getDisplayName());
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
