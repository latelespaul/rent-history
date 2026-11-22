package com.rentrix.rentrixserver.user.profile;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.rating.RatingDetail;
import com.rentrix.rentrixserver.review.RatingAggregateId;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewService;
import com.rentrix.rentrixserver.review.dto.ReviewView;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
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
	
	public UserProfileView get(UUID userId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		User person = person(userId);
		boolean admin = caller.hasRole(Role.ADMIN);
		boolean landlord = access.isLandlord(person);
		boolean tenant = tenantVisible(me, person, admin);
		if (!landlord && !tenant) {
			throw ApiException.notFound("User");
		}
		return new UserProfileView(
			person.getPublicId(),
			PublicNames.shortName(person.getDisplayName()),
			YearMonth.from(person.getCreatedAt().atZone(ZoneOffset.UTC)),
			identity.badges(person),
			landlord ? rating(RatingSubjectType.LANDLORD, person) : null,
			landlord ? access.listedHomes(person) : null,
			tenant ? rating(RatingSubjectType.TENANT, person) : null);
	}
	
	/** {@code GET /users/{id}/reviews?as=LANDLORD|TENANT} (landlord by default), with the profile's visibility. */
	public PageResponse<ReviewView> reviews(UUID userId, TenancyParty as, AuthenticatedUser caller, Pageable pageable) {
		User me = currentUser(caller);
		User person = person(userId);
		boolean admin = caller.hasRole(Role.ADMIN);
		if (as == TenancyParty.TENANT) {
			if (!tenantVisible(me, person, admin)) {
				throw ApiException.notFound("User");
			}
			return reviews.about(person, ReviewDirection.LANDLORD_TO_TENANT, me, admin, pageable);
		}
		if (!access.isLandlord(person)) {
			throw ApiException.notFound("User");
		}
		return reviews.about(person, ReviewDirection.TENANT_TO_LANDLORD, me, admin, pageable);
	}
	
	private boolean tenantVisible(User me, User person, boolean admin) {
		return person.hasRole(Role.TENANT) && access.canSeeTenant(me, person, admin);
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
