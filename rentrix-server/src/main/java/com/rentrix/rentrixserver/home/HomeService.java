package com.rentrix.rentrixserver.home;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.home.dto.AddHomeRequest;
import com.rentrix.rentrixserver.home.dto.AddHomeResult;
import com.rentrix.rentrixserver.home.dto.AddressCheck;
import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationService;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyService;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.property.dto.CreatePropertyRequest;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.TenancyService;
import com.rentrix.rentrixserver.tenancy.dto.CreateTenancyRequest;
import com.rentrix.rentrixserver.tenancy.dto.TenancyView;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.ApprovalGuard;
import lombok.RequiredArgsConstructor;
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
