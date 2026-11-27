package com.rentrix.rentrixserver.user.profile;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.rating.RatingBreakdowns;
import com.rentrix.rentrixserver.rating.RatingDetail;
import com.rentrix.rentrixserver.review.RatingAggregateId;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewService;
import com.rentrix.rentrixserver.review.dto.ReviewView;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.EvidenceLevel;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyService;
import com.rentrix.rentrixserver.user.PublicLabels;
import com.rentrix.rentrixserver.user.PublicNames;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Person profiles and the reviews they received (§10). A profile the caller may not see at all answers
 * {@code 404}, like one that does not exist (D-60), so tenants cannot be found by probing ids (D-54).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {
	
	private final UserRepository users;
	private final RatingAggregateRepository aggregates;
	private final IdentityService identity;
	private final ProfileAccess access;
	private final ReviewService reviews;
	private final RatingBreakdowns breakdowns;
	private final TenancyRepository tenancies;
	
	public UserProfileView get(UUID userId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		User person = person(userId);
		boolean admin = caller.hasRole(Role.ADMIN);
		boolean landlord = access.isLandlord(person);
		boolean tenant = tenantVisible(me, person, admin);
		if (!landlord && !tenant) {
			throw ApiException.notFound("User");
		}
		boolean self = person.getId().equals(me.getId());
		return new UserProfileView(
			person.getPublicId(),
			landlord ? PublicLabels.OWNER : PublicLabels.TENANT,
			self || admin ? PublicNames.of(person) : null,
			YearMonth.from(person.getCreatedAt().atZone(ZoneOffset.UTC)),
			identity.trustBadges(person, agreementBacked(person, landlord)),
			landlord ? breakdowns.forLandlord(rating(RatingSubjectType.LANDLORD, person), person.getId()) : null,
			landlord ? access.listedHomes(person) : null,
			tenant && tenantReviewsReadable(me, person, admin)
				? breakdowns.forTenant(rating(RatingSubjectType.TENANT, person), person.getId()) : null);
	}
	
	/** {@code GET /users/{id}/reviews?as=LANDLORD|TENANT} (landlord by default), with the profile's visibility. */
	public PageResponse<ReviewView> reviews(UUID userId, TenancyParty as, AuthenticatedUser caller, Pageable pageable) {
		User me = currentUser(caller);
		User person = person(userId);
		boolean admin = caller.hasRole(Role.ADMIN);
		if (as == TenancyParty.TENANT) {
			// D-175: the tenant themself gets the same 404 as a stranger, so not even the list's existence shows.
			if (!tenantReviewsReadable(me, person, admin)) {
				throw ApiException.notFound("User");
			}
			return reviews.about(person, ReviewDirection.LANDLORD_TO_TENANT, me, admin, pageable);
		}
		if (!access.isLandlord(person)) {
			throw ApiException.notFound("User");
		}
		return reviews.about(person, ReviewDirection.TENANT_TO_LANDLORD, me, admin, pageable);
	}
	
	/** "Agreement verified" (D-168): a rental agreement backs one of their tenancies on the side the profile shows. */
	private boolean agreementBacked(User person, boolean landlord) {
		return landlord
			? tenancies.existsByLandlordIdAndEvidenceLevelAndStatusIn(person.getId(), EvidenceLevel.DOCUMENT,
				TenancyService.CONFIRMED)
			: tenancies.existsByTenantIdAndEvidenceLevelAndStatusIn(person.getId(), EvidenceLevel.DOCUMENT,
				TenancyService.CONFIRMED);
	}
	
	private boolean tenantVisible(User me, User person, boolean admin) {
		return person.hasRole(Role.TENANT) && access.canSeeTenant(me, person, admin);
	}
	
	/** D-175: the owners' reviews of a tenant, their count and rating, only for the owners D-54 allows and admins. */
	private boolean tenantReviewsReadable(User me, User person, boolean admin) {
		return person.hasRole(Role.TENANT) && access.canReadTenantReviews(me, person, admin);
	}
	
	private RatingDetail rating(RatingSubjectType type, User person) {
		return RatingDetail.of(aggregates.findById(new RatingAggregateId(type, person.getId())).orElse(null));
	}
	
	private User person(UUID userId) {
		return users.findByPublicId(userId)
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(() -> ApiException.notFound("User"));
	}
	
	private User currentUser(AuthenticatedUser caller) {
		return users.findByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue."));
	}
	
}
