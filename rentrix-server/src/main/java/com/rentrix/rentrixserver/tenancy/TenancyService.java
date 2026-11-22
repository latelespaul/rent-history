package com.rentrix.rentrixserver.tenancy;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.dto.CreateTenancyRequest;
import com.rentrix.rentrixserver.tenancy.dto.EndTenancyRequest;
import com.rentrix.rentrixserver.tenancy.dto.PartyRef;
import com.rentrix.rentrixserver.tenancy.dto.TenancyActions;
import com.rentrix.rentrixserver.tenancy.dto.TenancyPropertyRef;
import com.rentrix.rentrixserver.tenancy.dto.TenancyView;
import com.rentrix.rentrixserver.user.PublicNames;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.ApprovalGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The tenancy lifecycle (§6): a request by either party, confirmed by the other within 14 days, then ended by
 * either party. Guards at creation and confirmation (D-35): approved verification for the acting side (D-91),
 * email-verified accounts, valid dates, no overlapping tenancy of the same tenant on the same property (D-23), on a
 * whole home no overlap with any other confirmed tenancy (one tenancy at a time; ROOM and PG take flatmates) and
 * at most {@value #MAX_PENDING} pending requests started per user. Decline, cancel and end are never gated (D-91).
 * <p>A landlord names the tenant by email, and the answer never shows whether that email has an account: the
 * landlord sees only a masked email and no tenant until the tenancy is confirmed. An existing, email-verified account
 * with the TENANT role is linked internally so it can confirm or decline from {@code /me/tenancies}; otherwise a
 * single-use invite (§5.12) goes to the email and binds the tenancy to whoever claims it while signed in with that
 * same verified email (D-34). The overlap guard (D-23) for such a request runs at claim and at confirm, against
 * confirmed tenancies only.
 * <p>Visibility: a tenancy is readable by its parties and admins only; anyone else gets {@code 404} (D-60).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenancyService {

	/** Time the counterparty has to confirm before the request expires (§6). */
	public static final Duration CONFIRM_WINDOW = Duration.ofDays(14);
	public static final int MAX_PENDING = 5;

	/** States that hold the tenant's dates on a property (overlap guard, D-23). */
	static final Set<TenancyStatus> HOLDS_DATES =
		EnumSet.of(TenancyStatus.PENDING_CONFIRMATION, TenancyStatus.ACTIVE, TenancyStatus.ENDED);
	/** Confirmed by both parties (D-34). */
	public static final List<TenancyStatus> CONFIRMED = List.of(TenancyStatus.ACTIVE, TenancyStatus.ENDED);

	private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

	private final TenancyRepository tenancies;
	private final RentRecordRepository rentRecords;
	private final InviteTokenRepository invites;
	private final PropertyRepository properties;
	private final UserRepository users;
	private final ApprovalGuard approvals;
	private final TenancyNotifier notifier;
	private final Clock clock;

	// ---------------------------------------------------------------- reads

	public TenancyView get(UUID publicId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		return view(readable(publicId, me, caller), me, caller);
	}

	/** {@code GET /me/tenancies?role=&status=}: the caller's tenancies on either side, or the one named. */
	public PageResponse<TenancyView> mine(AuthenticatedUser caller, TenancyParty role, Set<TenancyStatus> statuses,
		Pageable pageable) {
		User me = currentUser(caller);
		Set<TenancyStatus> filter = statuses == null || statuses.isEmpty() ? EnumSet.allOf(TenancyStatus.class) : statuses;
		Page<Tenancy> page;
		if (role == TenancyParty.LANDLORD) {
			page = tenancies.findByLandlordIdAndStatusIn(me.getId(), filter, pageable);
		} else if (role == TenancyParty.TENANT) {
			page = tenancies.findByTenantIdAndStatusIn(me.getId(), filter, pageable);
		} else {
			page = tenancies.findByPartyAndStatusIn(me.getId(), filter, pageable);
		}
		return PageResponse.of(page, t -> view(t, me, caller));
	}

	// --------------------------------------------------------------- writes

	/** {@code POST /tenancies}: a {@code PENDING_CONFIRMATION} request from the property's landlord or a tenant. */
	@Transactional
	public TenancyView create(CreateTenancyRequest request, AuthenticatedUser caller) {
		return create(request, caller, false);
	}
	
	/**
	 * A tenancy the tenant records themselves through "Add your home" (D-139, D-142): it stands at once, one-sided,
	 * without waiting for the owner; the team verifies it later from the rental agreement (D-142).
	 */
	@Transactional
	public TenancyView recordOwn(CreateTenancyRequest request, AuthenticatedUser caller) {
		return create(request, caller, true);
	}
	
	private TenancyView create(CreateTenancyRequest request, AuthenticatedUser caller, boolean selfRecorded) {
		// The row lock serialises one user's creates, so the pending cap holds under concurrent requests.
		User me = users.lockByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(TenancyService::unauthenticated);
		Property property = properties.findByPublicIdAndDeletedAtIsNull(request.propertyId())
			.filter(p -> p.getStatus().isPublic() || p.getLandlord().getId().equals(me.getId()))
			.orElseThrow(() -> ApiException.notFound("Property"));
		boolean asLandlord = property.getLandlord().getId().equals(me.getId());
		// A home a tenant added while its landlord is not on the platform (D-139) has nobody to confirm, and a
		// tenant's own "Add your home" record never waits for the owner (D-142): the period stands at once,
		// one-sided and without the green tick (D-132).
		boolean oneSided = !asLandlord && (selfRecorded || UnclaimedLandlord.is(property.getLandlord()));
		requireDates(request.startDate(), request.endDate());
		String inviteEmail = null;
		if (asLandlord) {
			requireRole(me, Role.LANDLORD);
			if (!property.getStatus().isPublic()) {
				throw ApiException.rule("property_not_listed",
					"Tenancies can be recorded only on a listing an admin approved.");
			}
			inviteEmail = normaliseEmail(request.counterpartyEmail());
			if (inviteEmail == null) {
				throw ApiException.validation(List.of(new ApiProblem.FieldError("counterpartyEmail", "required",
					"Enter the tenant's email address.")));
			}
			if (inviteEmail.equals(normaliseEmail(me.getEmail()))) {
				throw ApiException.rule("invite_self", "You cannot invite yourself as the tenant.");
			}
		} else {
			requireRole(me, Role.TENANT);
			if (property.getStatus() != PropertyStatus.PUBLISHED) {
				throw ApiException.rule("property_not_listed", "This home is not listed yet.");
			}
			if (request.counterpartyEmail() != null && !request.counterpartyEmail().isBlank()) {
				throw ApiException.validation(List.of(new ApiProblem.FieldError("counterpartyEmail", "not_allowed",
					"Only the landlord names the other party; the property's landlord confirms your request.")));
			}
		}
		requireEmailVerified(me);
		approvals.require(me, asLandlord ? Role.LANDLORD : Role.TENANT);
		if (!oneSided
			&& tenancies.countInitiatedBy(me.getId(), TenancyStatus.PENDING_CONFIRMATION) >= MAX_PENDING) {
			throw ApiException.rule("too_many_pending", "You already have " + MAX_PENDING
				+ " tenancy requests waiting for confirmation. Wait for them or cancel one first.");
		}

		requireHomeFree(property, null, request.startDate(), request.endDate(), me);

		Tenancy tenancy = new Tenancy();
		tenancy.setProperty(property);
		tenancy.setLandlord(property.getLandlord());
		tenancy.setStartDate(request.startDate());
		tenancy.setEndDate(request.endDate());
		tenancy.setInitialRentInr(request.initialRentInr());
		tenancy.setDepositInr(request.depositInr());
		tenancy.setStatus(TenancyStatus.PENDING_CONFIRMATION);
		tenancy.setInitiatedBy(asLandlord ? TenancyParty.LANDLORD : TenancyParty.TENANT);
		if (asLandlord) {
			tenancy.setInviteEmail(inviteEmail);
			users.findByEmail(inviteEmail)
				.filter(user -> user.getDeletedAt() == null && user.getEmailVerifiedAt() != null
					&& user.hasRole(Role.TENANT))
				.ifPresent(tenancy::setTenant);
		} else {
			tenancy.setTenant(me);
			requireNoOverlap(tenancy, HOLDS_DATES);
		}
		if (oneSided) {
			return startOneSided(tenancy, me, caller);
		}
		Tenancy saved = tenancies.saveAndFlush(tenancy);

		if (saved.getTenant() == null) {
			String raw = InviteTokens.newToken();
			InviteToken invite = new InviteToken();
			invite.setTenancy(saved);
			invite.setTokenHash(InviteTokens.hash(raw));
			invite.setEmail(inviteEmail);
			invite.setExpiresAt(now().plus(CONFIRM_WINDOW));
			invites.save(invite);
			notifier.invite(saved, raw);
		} else {
			notifier.confirmRequest(saved, asLandlord ? saved.getTenant() : saved.getLandlord());
		}
		return view(saved, me, caller);
	}

	/**
	 * {@code POST /tenancy-invites/claim}: binds a landlord's invite to the caller, whose verified email must equal
	 * the invited one (D-34). The caller then confirms or declines like any counterparty.
	 */
	@Transactional
	public TenancyView claim(String rawToken, AuthenticatedUser caller) {
		User me = currentUser(caller);
		InviteToken invite = invites.findByTokenHash(InviteTokens.hash(rawToken.trim()))
			.filter(i -> i.getUsedAt() == null && i.getExpiresAt().isAfter(now()))
			.orElseThrow(() -> ApiException.notFound("Invite"));
		if (me.getEmailVerifiedAt() == null || !invite.getEmail().equals(normaliseEmail(me.getEmail()))) {
			throw new ApiException(HttpStatus.FORBIDDEN, "invite_email_mismatch",
				"This invite was sent to another email address. Sign in with that Google account.");
		}
		Tenancy tenancy = tenancies.lockById(invite.getTenancy().getId())
			.orElseThrow(() -> ApiException.notFound("Invite"));
		if (tenancy.getStatus() != TenancyStatus.PENDING_CONFIRMATION || tenancy.getTenant() != null) {
			throw ApiException.conflict("invalid_transition", "This invite is no longer open.");
		}
		requireRole(me, Role.TENANT);
		tenancy.setTenant(me);
		requireNoOverlap(tenancy, CONFIRMED);
		invite.setUsedAt(now());
		invites.save(invite);
		return view(tenancies.saveAndFlush(tenancy), me, caller);
	}

	/**
	 * {@code POST /tenancies/{id}/confirm}: the counterparty confirms → {@code ACTIVE}, or {@code ENDED} when the
	 * request carries an end date (D-34). The initial rent becomes the first {@code CONFIRMED} rent record (D-25).
	 */
	@Transactional
	public TenancyView confirm(UUID publicId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Tenancy tenancy = writable(publicId, me, caller);
		requirePending(tenancy, "confirmed");
		requireCounterparty(tenancy, me);
		requireEmailVerified(me);
		approvals.require(me, roleOf(sideOf(tenancy, me)));
		requireNoOverlap(tenancy, CONFIRMED);
		requireHomeFree(tenancy.getProperty(), tenancy.getId(), tenancy.getStartDate(), tenancy.getEndDate(), me);
		Instant now = now();
		tenancy.setStatus(tenancy.getEndDate() == null ? TenancyStatus.ACTIVE : TenancyStatus.ENDED);
		tenancy.setConfirmedAt(now);
		tenancy.setEvidenceLevel(EvidenceLevel.MUTUAL);
		if (tenancy.getEndDate() != null) {
			tenancy.setEndedAt(now);
		}
		Tenancy saved = tenancies.saveAndFlush(tenancy);
		RentRecord first = new RentRecord();
		first.setTenancy(saved);
		first.setEffectiveFrom(saved.getStartDate().withDayOfMonth(1));
		first.setMonthlyRentInr(saved.getInitialRentInr());
		first.setAddedBy(initiator(saved));
		first.setStatus(RentRecordStatus.CONFIRMED);
		rentRecords.save(first);
		notifier.confirmed(saved, initiator(saved));
		return view(saved, me, caller);
	}

	/**
	 * A one-sided tenant period (D-139, D-142): {@code ACTIVE}, or {@code ENDED} with an end date, at once and
	 * without {@code confirmedAt}; the initial rent is a {@code PROPOSED} record (one side only, D-25). Owner
	 * confirmation is optional and not asked for; a real landlord is only told the record exists.
	 */
	private TenancyView startOneSided(Tenancy tenancy, User me, AuthenticatedUser caller) {
		Instant now = now();
		tenancy.setStatus(tenancy.getEndDate() == null ? TenancyStatus.ACTIVE : TenancyStatus.ENDED);
		if (tenancy.getEndDate() != null) {
			tenancy.setEndedAt(now);
		}
		Tenancy saved = tenancies.saveAndFlush(tenancy);
		RentRecord first = new RentRecord();
		first.setTenancy(saved);
		first.setEffectiveFrom(saved.getStartDate().withDayOfMonth(1));
		first.setMonthlyRentInr(saved.getInitialRentInr());
		first.setAddedBy(me);
		first.setStatus(RentRecordStatus.PROPOSED);
		rentRecords.save(first);
		notifier.recordedByTenant(saved, saved.getLandlord());
		return view(saved, me, caller);
	}

	/** {@code POST /tenancies/{id}/decline}: the counterparty refuses; never gated (D-91). */
	@Transactional
	public TenancyView decline(UUID publicId, String reason, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Tenancy tenancy = writable(publicId, me, caller);
		requirePending(tenancy, "declined");
		requireCounterparty(tenancy, me);
		tenancy.setStatus(TenancyStatus.DECLINED);
		tenancy.setDeclineReason(reason == null || reason.isBlank() ? null : reason.trim());
		Tenancy saved = tenancies.saveAndFlush(tenancy);
		notifier.declined(saved, initiator(saved));
		return view(saved, me, caller);
	}

	/** {@code POST /tenancies/{id}/cancel}: the initiator withdraws a pending request; never gated (D-91). */
	@Transactional
	public TenancyView cancel(UUID publicId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Tenancy tenancy = writable(publicId, me, caller);
		requirePending(tenancy, "cancelled");
		if (sideOf(tenancy, me) != tenancy.getInitiatedBy()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_initiator",
				"Only the party who sent the request can cancel it.");
		}
		tenancy.setStatus(TenancyStatus.CANCELLED);
		closeInvites(tenancy);
		return view(tenancies.saveAndFlush(tenancy), me, caller);
	}

	/**
	 * {@code POST /tenancies/{id}/end}: either party (or an admin) sets the last day of an {@code ACTIVE} tenancy;
	 * not gated (D-91). The other party is told and can dispute it by reporting the tenancy (D-36).
	 */
	@Transactional
	public TenancyView end(UUID publicId, EndTenancyRequest request, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Tenancy tenancy = writable(publicId, me, caller);
		if (tenancy.getStatus() != TenancyStatus.ACTIVE) {
			throw ApiException.conflict("invalid_transition", "A " + tenancy.getStatus() + " tenancy cannot be ended.");
		}
		LocalDate endDate = request.endDate();
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		if (endDate.isAfter(today())) {
			errors.add(new ApiProblem.FieldError("endDate", "not_in_future", "The end date cannot be in the future."));
		} else if (endDate.isBefore(tenancy.getStartDate())) {
			errors.add(new ApiProblem.FieldError("endDate", "before_start", "The end date is before the start date."));
		} else {
			rentRecords.findByTenancyIdOrderByEffectiveFromAsc(tenancy.getId()).stream()
				.filter(r -> r.getStatus() != RentRecordStatus.DISPUTED)
				.map(RentRecord::getEffectiveFrom)
				.max(Comparator.naturalOrder())
				.filter(last -> endDate.isBefore(last))
				.ifPresent(last -> errors.add(new ApiProblem.FieldError("endDate", "before_rent_record",
					"A rent record starts on " + last + ", after this end date.")));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		tenancy.setEndDate(endDate);
		tenancy.setDepositRefundedInr(request.depositRefundedInr());
		tenancy.setStatus(TenancyStatus.ENDED);
		tenancy.setEndedAt(now());
		Tenancy saved = tenancies.saveAndFlush(tenancy);
		TenancyParty side = sideOf(saved, me);
		if (side != null) {
			User other = side == TenancyParty.LANDLORD ? saved.getTenant() : saved.getLandlord();
			notifier.ended(saved, other);
		} else {
			notifier.ended(saved, saved.getLandlord());
			notifier.ended(saved, saved.getTenant());
		}
		return view(saved, me, caller);
	}

	/** Pending requests past their 14-day window become {@code EXPIRED} (§6); run by {@link TenancyExpiryJob}. */
	@Transactional
	public int expirePending() {
		Instant cutoff = now().minus(CONFIRM_WINDOW);
		int expired = 0;
		for (UUID id : tenancies.findPublicIdsByStatusAndCreatedAtBefore(TenancyStatus.PENDING_CONFIRMATION, cutoff)) {
			Tenancy tenancy = tenancies.lockByPublicId(id).orElse(null);
			if (tenancy == null || tenancy.getStatus() != TenancyStatus.PENDING_CONFIRMATION
				|| !tenancy.getCreatedAt().isBefore(cutoff)) {
				continue;
			}
			tenancy.setStatus(TenancyStatus.EXPIRED);
			closeInvites(tenancy);
			tenancies.save(tenancy);
			expired++;
		}
		return expired;
	}

	// -------------------------------------------------------------- helpers

	/** A party of the tenancy or an admin, else 404 (no existence leak, D-60). */
	Tenancy readable(UUID publicId, User me, AuthenticatedUser caller) {
		return visible(tenancies.findByPublicId(publicId), me, caller);
	}

	/** {@link #readable} with the tenancy row locked, for a state transition. */
	private Tenancy writable(UUID publicId, User me, AuthenticatedUser caller) {
		return visible(tenancies.lockByPublicId(publicId), me, caller);
	}

	private static Tenancy visible(Optional<Tenancy> tenancy, User me, AuthenticatedUser caller) {
		return tenancy.filter(t -> sideOf(t, me) != null || caller.hasRole(Role.ADMIN))
			.orElseThrow(() -> ApiException.notFound("Tenancy"));
	}

	/** The caller's side of the tenancy, or {@code null} when they are not a party. */
	static TenancyParty sideOf(Tenancy tenancy, User user) {
		if (tenancy.getLandlord().getId().equals(user.getId())) {
			return TenancyParty.LANDLORD;
		}
		if (tenancy.getTenant() != null && tenancy.getTenant().getId().equals(user.getId())) {
			return TenancyParty.TENANT;
		}
		return null;
	}

	static Role roleOf(TenancyParty side) {
		return side == TenancyParty.LANDLORD ? Role.LANDLORD : Role.TENANT;
	}

	static User initiator(Tenancy tenancy) {
		return tenancy.getInitiatedBy() == TenancyParty.LANDLORD ? tenancy.getLandlord() : tenancy.getTenant();
	}

	User currentUser(AuthenticatedUser caller) {
		return users.findByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(TenancyService::unauthenticated);
	}

	LocalDate today() {
		return LocalDate.now(clock);
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}

	private void requirePending(Tenancy tenancy, String verb) {
		if (tenancy.getStatus() != TenancyStatus.PENDING_CONFIRMATION) {
			throw ApiException.conflict("invalid_transition",
				"A " + tenancy.getStatus() + " tenancy cannot be " + verb + ".");
		}
		if (tenancy.getCreatedAt().plus(CONFIRM_WINDOW).isBefore(now())) {
			throw ApiException.conflict("invalid_transition", "This request expired; ask for a new one.");
		}
	}

	private static void requireCounterparty(Tenancy tenancy, User me) {
		TenancyParty side = sideOf(tenancy, me);
		if (side == null || side == tenancy.getInitiatedBy()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_counterparty",
				"Only the other party can confirm or decline this request.");
		}
	}

	private void requireDates(LocalDate start, LocalDate end) {
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		LocalDate today = today();
		if (start.isAfter(today)) {
			errors.add(new ApiProblem.FieldError("startDate", "not_in_future",
				"Record a tenancy once it has started."));
		}
		if (end != null && end.isBefore(start)) {
			errors.add(new ApiProblem.FieldError("endDate", "before_start", "The end date is before the start date."));
		} else if (end != null && end.isAfter(today)) {
			errors.add(new ApiProblem.FieldError("endDate", "not_in_future",
				"Leave the end date empty for a current tenancy."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
	}

	/** Whether the property is let as a whole home, one tenancy at a time (D-23); ROOM and PG take flatmates. */
	public static boolean wholeHome(Property property) {
		return property.getPropertyType() != PropertyType.ROOM && property.getPropertyType() != PropertyType.PG;
	}

	/**
	 * A whole home holds one tenancy at a time (D-23): a request or confirmation whose dates overlap a confirmed
	 * tenancy on the same property, of any tenant, is refused with {@code 409 tenancy_overlap} naming that tenancy's
	 * period. On a home with a real landlord only tenancies both sides confirmed or the landlord started hold dates:
	 * a tenant's one-sided record (D-142) never blocks the landlord's tenancies. Nothing is ended automatically; the home's landlord also gets the tenancy's id so they can end it
	 * first. The property row lock serialises concurrent confirmations on one home. ROOM and PG are exempt.
	 */
	private void requireHomeFree(Property property, Long excludeId, LocalDate start, LocalDate end, User me) {
		if (!wholeHome(property)) {
			return;
		}
		properties.lockById(property.getId());
		tenancies.findOverlappingOnProperty(property.getId(), excludeId == null ? -1L : excludeId, CONFIRMED, start,
				end == null ? OPEN_END : end).stream()
			.filter(t -> t.getConfirmedAt() != null || t.getInitiatedBy() == TenancyParty.LANDLORD
				|| UnclaimedLandlord.is(property.getLandlord()))
			.findFirst()
			.ifPresent(clash -> {
				Map<String, String> conflict = new LinkedHashMap<>();
				conflict.put("from", YearMonth.from(clash.getStartDate()).toString());
				if (clash.getEndDate() != null) {
					conflict.put("to", YearMonth.from(clash.getEndDate()).toString());
				}
				conflict.put("status", clash.getStatus().name());
				boolean ownersView = property.getLandlord().getId().equals(me.getId());
				if (ownersView) {
					conflict.put("tenancyId", clash.getPublicId().toString());
				}
				throw ApiException.tenancyOverlap(clash.getEndDate() == null
					? "This home already has a current tenancy since " + conflict.get("from")
						+ ". It must end before a new one can start."
					: "This home already has a tenancy from " + conflict.get("from") + " to " + conflict.get("to")
						+ " that overlaps these dates.", conflict);
			});
	}

	/**
	 * D-23: one tenant cannot hold two overlapping tenancies on the same property. A tenant's own request is checked
	 * against pending ones too; claim and confirm only against confirmed ones, so a sibling pending request never blocks.
	 */
	private void requireNoOverlap(Tenancy tenancy, Collection<TenancyStatus> against) {
		users.lockByPublicId(tenancy.getTenant().getPublicId());
		boolean overlap = !tenancies.lockOverlapping(tenancy.getProperty().getId(), tenancy.getTenant().getId(),
			tenancy.getId() == null ? -1L : tenancy.getId(), against, tenancy.getStartDate(),
			tenancy.getEndDate() == null ? OPEN_END : tenancy.getEndDate()).isEmpty();
		if (overlap) {
			throw ApiException.conflict("tenancy_overlap",
				"This tenant already has a tenancy on this home for overlapping dates.");
		}
	}

	private static void requireRole(User user, Role role) {
		if (!user.hasRole(role)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "role_required",
				"Add the " + role.name().toLowerCase(Locale.ROOT) + " role to your account first.");
		}
	}

	private static void requireEmailVerified(User user) {
		if (user.getEmailVerifiedAt() == null) {
			throw ApiException.rule("email_not_verified", "Your email address is not verified.");
		}
	}

	private void closeInvites(Tenancy tenancy) {
		if (tenancy.getId() == null) {
			return;
		}
		List<InviteToken> open = invites.findByTenancyIdAndUsedAtIsNull(tenancy.getId());
		open.forEach(invite -> invite.setUsedAt(now()));
		invites.saveAll(open);
	}

	private static String normaliseEmail(String email) {
		return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	private static ApiException unauthenticated() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue.");
	}

	TenancyView view(Tenancy t, User me, AuthenticatedUser caller) {
		TenancyParty side = sideOf(t, me);
		boolean admin = caller.hasRole(Role.ADMIN);
		boolean pending = t.getStatus() == TenancyStatus.PENDING_CONFIRMATION;
		boolean counterparty = side != null && side != t.getInitiatedBy();
		TenancyActions actions = side == null && !admin ? TenancyActions.NONE : new TenancyActions(
			pending && counterparty,
			pending && counterparty,
			pending && side == t.getInitiatedBy(),
			t.getStatus() == TenancyStatus.ACTIVE,
			side != null && CONFIRMED.contains(t.getStatus()));
		boolean tenantHidden = side == TenancyParty.LANDLORD && !admin && t.getInitiatedBy() == TenancyParty.LANDLORD
			&& !CONFIRMED.contains(t.getStatus());
		Property p = t.getProperty();
		boolean confirmed = CONFIRMED.contains(t.getStatus());
		// The latest confirmed rent in effect today, and the date it applies from.
		Optional<RentRecord> inEffect = confirmed
			? rentRecords.findByTenancyIdOrderByEffectiveFromAsc(t.getId()).stream()
			.filter(r -> r.getStatus() == RentRecordStatus.CONFIRMED && !r.getEffectiveFrom().isAfter(today()))
			.reduce((a, b) -> b)
			: Optional.empty();
		Long currentRent = confirmed ? inEffect.map(RentRecord::getMonthlyRentInr).orElse(t.getInitialRentInr()) : null;
		// Rent records start on the first of a month; the rent never applies before the tenancy starts.
		LocalDate currentRentFrom = confirmed
			? inEffect.map(RentRecord::getEffectiveFrom).filter(from -> from.isAfter(t.getStartDate()))
			.orElse(t.getStartDate())
			: null;
		return new TenancyView(
			t.getPublicId(),
			new TenancyPropertyRef(p.getPublicId(), p.getBuildingName(), p.getLocality(), CityRef.of(p.getCity()),
				p.getUnitLabel(), p.getPropertyType()),
			side,
			party(t.getLandlord()),
			t.getTenant() == null || tenantHidden ? null : party(t.getTenant()),
			admin ? t.getInviteEmail() : side == TenancyParty.LANDLORD ? maskEmail(t.getInviteEmail()) : null,
			t.getStartDate(),
			t.getEndDate(),
			t.getInitialRentInr(),
			currentRent,
			currentRentFrom,
			t.getDepositInr(),
			t.getDepositRefundedInr(),
			t.getStatus(),
			t.getInitiatedBy(),
			t.getDeclineReason(),
			t.getEvidenceLevel(),
			t.getCreatedAt(),
			pending ? t.getCreatedAt().plus(CONFIRM_WINDOW) : null,
			t.getConfirmedAt(),
			t.getEndedAt(),
			actions);
	}

	/** {@code priya@gmail.com} → {@code p***@g***.com}: enough for the landlord to recognise, not to read. */
	static String maskEmail(String email) {
		if (email == null) {
			return null;
		}
		int at = email.indexOf('@');
		String local = at > 0 ? email.substring(0, at) : email;
		String domain = at >= 0 ? email.substring(at + 1) : "";
		int dot = domain.lastIndexOf('.');
		String suffix = dot >= 0 ? domain.substring(dot) : "";
		return local.charAt(0) + "***@" + (domain.isEmpty() ? "" : domain.charAt(0)) + "***" + suffix;
	}

	static PartyRef party(User user) {
		if (UnclaimedLandlord.is(user)) {
			return new PartyRef(null, UnclaimedLandlord.PUBLIC_NAME);
		}
		return new PartyRef(user.getPublicId(), PublicNames.shortName(user.getDisplayName()));
	}

}
