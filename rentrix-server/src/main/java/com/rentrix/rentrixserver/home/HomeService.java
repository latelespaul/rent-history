package com.rentrix.rentrixserver.home;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.persistence.TimestampedEntity;
import com.rentrix.rentrixserver.edit.EditHistory;
import com.rentrix.rentrixserver.edit.EditTarget;
import com.rentrix.rentrixserver.home.dto.AddHomeRequest;
import com.rentrix.rentrixserver.home.dto.AddHomeResult;
import com.rentrix.rentrixserver.home.dto.AddressCheck;
import com.rentrix.rentrixserver.home.dto.UpdateHomeRequest;
import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationService;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyService;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.property.dto.CreatePropertyRequest;
import com.rentrix.rentrixserver.property.dto.PropertyDetail;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.TenancyService;
import com.rentrix.rentrixserver.tenancy.dto.CreateTenancyRequest;
import com.rentrix.rentrixserver.tenancy.dto.TenancyView;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.ApprovalGuard;
import com.rentrix.rentrixserver.verification.VerificationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "Add your home" (D-139): one call stores the home, the caller's tenancy and its first rent record, and the owner's
 * contact as the tenant gave it. Open by default (D-132): everything shows at once without the green tick, and the
 * only gate is the verified phone. A tenant who adds a home they own gets the {@code LANDLORD} role (D-16).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {
	
	private final UserRepository users;
	private final PropertyRepository properties;
	private final PropertyService propertyService;
	private final TenancyService tenancies;
	private final UnclaimedLandlord unclaimed;
	private final ApprovalGuard approvals;
	private final AccessTokenService tokens;
	private final CityService cityService;
	private final LocationService locations;
	private final EditHistory history;
	
	/**
	 * Step 1's PIN code against the city (a hard error: its prefixes, D-84) and against the picked area (only a
	 * warning, since PIN codes cross area borders and the area data is incomplete; firstmate 2026-11-23 14:50).
	 */
	@Transactional(readOnly = true)
	public AddressCheck checkAddress(String city, String pincode, UUID areaId, AuthenticatedUser caller) {
		// Never through the throwing checks: their transaction would mark this one rollback-only.
		String error = cityService.pincodeProblem(city, pincode == null ? null : pincode.trim());
		if (areaId == null) {
			return new AddressCheck(error, null, null);
		}
		Area area = locations.areaFilter(areaId, caller, "area");
		List<String> pins = area.pincodeList();
		String warning = error == null && pincode != null && !pins.isEmpty() && !pins.contains(pincode.trim())
			? pincode.trim() + " is usually not in " + area.getName() + "; check the PIN."
			: null;
		return new AddressCheck(error, warning, pins);
	}
	
	@Transactional
	public AddHomeResult add(AddHomeRequest request, AuthenticatedUser caller) {
		check(request);
		User me = users.findByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue."));
		Role role = request.relation() == HomeRelation.OWN ? Role.LANDLORD : Role.TENANT;
		approvals.require(me, role);
		boolean newRole = me.getRoles().add(role);
		
		Property property;
		if (request.propertyId() != null) {
			property = propertyService.publicHome(request.propertyId());
		} else {
			boolean own = request.relation() == HomeRelation.OWN;
			CreatePropertyRequest home = request.home();
			boolean pendingCity = false;
			if (request.newCity() != null) {
				City city = cityService.findOrCreatePending(request.newCity().name(), request.newCity().state());
				pendingCity = !city.isLive();
				home = withCity(home, city.getCode());
			}
			property = propertyService.newHome(home, own ? me : unclaimed.get(), me, pendingCity);
			if (!own) {
				property.setAddedBy(me);
				AddHomeRequest.Owner owner = request.owner();
				if (owner != null) {
					property.setOwnerName(blankToNull(owner.name()));
					property.setOwnerEmail(blankToNull(owner.email()));
					property.setOwnerPhone(blankToNull(owner.phone()));
				}
			}
			property = properties.saveAndFlush(property);
		}
		
		// The caller's token may predate the new role; the tenancy rules read the roles from the database row.
		AuthenticatedUser acting = newRole ? new AuthenticatedUser(me.getPublicId(), me.getRoles()) : caller;
		TenancyView tenancy = request.relation() == HomeRelation.RENT
			? tenancies.recordOwn(new CreateTenancyRequest(property.getPublicId(), null, request.startDate(),
				request.endDate(), request.monthlyRentInr(), request.depositInr()), acting)
			: null;
		return new AddHomeResult(propertyService.detailOf(property, acting), tenancy,
			newRole ? tokens.issue(me.getPublicId(), me.getRoles()) : null);
	}
	
	/**
	 * {@code PATCH /me/homes/{id}}: the person who added a home corrects its address or size (D-156), with the add-home
	 * checks. A change of a home with the team's tick (or a rejection) puts it back to {@code UNVERIFIED}, so it
	 * returns to the admin queue; every changed field is kept in the edit history for admins. Once other people have
	 * tenancies on the home its address is theirs too: only the size may change then.
	 */
	@Transactional
	public PropertyDetail update(UUID publicId, UpdateHomeRequest request, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Property property = created(publicId, me);
		approvals.require(me, property.getAddedBy() != null ? Role.TENANT : Role.LANDLORD);
		EditHistory.Changes changes = propertyService.editHome(property, request, me,
			tenancies.othersOnHome(property, me));
		if (!changes.isEmpty()) {
			VerificationState state = VerificationState.of(property.getVerificationStatus());
			property.setVerificationStatus(VerificationState.UNVERIFIED);
			history.edited(me, EditTarget.PROPERTY, property.getId(), property.getPublicId(),
				state == VerificationState.VERIFIED, changes);
			log.info("Home {} edited by its creator {}", property.getPublicId(), me.getPublicId());
		}
		return propertyService.detailOf(properties.saveAndFlush(property), caller);
	}
	
	/**
	 * {@code DELETE /me/homes/{id}}: the person who added a home removes it with their own tenancies and reviews on it
	 * (D-156). Refused while other people have tenancies there ({@code 409 home_in_use}).
	 */
	@Transactional
	public void delete(UUID publicId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Property property = created(publicId, me);
		if (tenancies.othersOnHome(property, me)) {
			throw ApiException.conflict("home_in_use",
				"Other people have tenancies on this home, so it cannot be deleted. Ask our team to remove it.");
		}
		tenancies.deleteOwnOnHome(property, me);
		boolean wasVerified = VerificationState.of(property.getVerificationStatus()) == VerificationState.VERIFIED;
		property.setDeletedAt(TimestampedEntity.now());
		properties.saveAndFlush(property);
		history.deleted(me, EditTarget.PROPERTY, property.getId(), property.getPublicId(), wasVerified, "home",
			property.getBuildingName() == null ? property.getLocality()
				: property.getBuildingName() + ", " + property.getLocality());
		log.info("Home {} deleted by its creator {}", property.getPublicId(), me.getPublicId());
	}
	
	/** A home the caller added (D-156): 404 when it does not exist, 403 when it is someone else's. */
	private Property created(UUID publicId, User me) {
		Property property = properties.findByPublicIdAndDeletedAtIsNull(publicId)
			.filter(p -> p.getStatus().isPublic() || PropertyService.createdBy(p, me.getPublicId()))
			.orElseThrow(() -> ApiException.notFound("Property"));
		if (!PropertyService.createdBy(property, me.getPublicId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_creator",
				"Only the person who added this home can change or delete it.");
		}
		return property;
	}
	
	private User currentUser(AuthenticatedUser caller) {
		return users.findByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue."));
	}
	
	private static CreatePropertyRequest withCity(CreatePropertyRequest r, String city) {
		return new CreatePropertyRequest(city, r.locality(), r.buildingName(), r.street(), r.unitLabel(), r.pincode(),
			r.propertyType(), r.bedrooms(), r.bathrooms(), r.areaSqft(), r.furnishing(), r.parking(), r.description(),
			r.location(), r.homeSize(), r.homeSizeOther());
	}
	
	private static void check(AddHomeRequest request) {
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		if ((request.propertyId() == null) == (request.home() == null)) {
			errors.add(new ApiProblem.FieldError("home", "required", "Give either the new home or the one you picked."));
		}
		// A standalone apartment (no society) is named by its building (captain 2026-11-23 11:35); a house or
		// villa may go without one.
		CreatePropertyRequest home = request.home();
		if (home != null && home.propertyType() == PropertyType.APARTMENT && !StringUtils.hasText(home.buildingName())
			&& (home.location() == null || home.location().societyId() == null && home.location().newSociety() == null)) {
			errors.add(new ApiProblem.FieldError("home.buildingName", "required", "Enter the building name."));
		}
		if (request.relation() == HomeRelation.RENT) {
			if (request.startDate() == null) {
				errors.add(new ApiProblem.FieldError("startDate", "required", "Enter when you moved in."));
			}
		} else if (request.relation() == HomeRelation.OWN) {
			if (request.startDate() != null || request.monthlyRentInr() != null || request.owner() != null) {
				errors.add(new ApiProblem.FieldError("relation", "not_allowed",
					"An owner adds the home only; record a tenancy on it afterwards."));
			}
			if (request.propertyId() != null) {
				throw ApiException.rule("claim_not_available",
					"This home is already listed. Claiming a listed home is checked by an admin; contact support.");
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
	}
	
	private static String blankToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
	
}
