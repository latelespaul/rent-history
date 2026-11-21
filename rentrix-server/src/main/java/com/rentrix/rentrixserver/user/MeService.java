package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.auth.AuthService;
import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Reads and changes the caller's own account (§12.2, D-16, D-73). */
@Service
@RequiredArgsConstructor
public class MeService {
	
	/** E.164 (§5.1): {@code +}, country code, 7–15 digits in all. */
	static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{6,14}$");
	
	private final UserRepository users;
	private final CityRepository cities;
	private final CityService cityService;
	private final UserViews userViews;
	private final AuthService auth;
	private final Clock clock;
	
	@Transactional(readOnly = true)
	public UserView get(UUID publicId) {
		return userViews.of(load(publicId));
	}
	
	/** Once, on first sign-in: sets the roles (and optional home city) and returns a token carrying them. */
	@Transactional
	public MeController.MeUpdate onboard(UUID publicId, MeController.OnboardingRequest request) {
		User user = load(publicId);
		if (user.getOnboardedAt() != null) {
			throw ApiException.conflict("already_onboarded", "Onboarding is already done. Add a role in settings.");
		}
		requireSelfServiceRoles(request.roles(), "roles");
		if (request.homeCity() != null && !request.homeCity().isBlank()) {
			setHomeCity(user, request.homeCity());
		}
		user.getRoles().addAll(request.roles());
		user.setOnboardedAt(clock.instant().truncatedTo(ChronoUnit.MILLIS));
		return changed(user);
	}
	
	@Transactional
	public MeController.MeUpdate update(UUID publicId, MeController.UpdateMeRequest request) {
		User user = load(publicId);
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		String displayName = request.displayName() == null ? null : request.displayName().trim();
		if (displayName != null && displayName.length() < 2) {
			errors.add(new ApiProblem.FieldError("displayName", "size", "Use 2 to 60 characters."));
		}
		String phone = request.phone() == null ? null : request.phone().trim();
		if (phone != null && !phone.isEmpty() && !PHONE.matcher(phone).matches()) {
			errors.add(new ApiProblem.FieldError("phone", "pattern", "Use international format, e.g. +919876543210."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		if (request.addRoles() != null) {
			requireSelfServiceRoles(request.addRoles(), "addRoles");
		}
		if (displayName != null) {
			user.setDisplayName(displayName);
		}
		if (phone != null) {
			user.setPhone(phone.isEmpty() ? null : phone);
		}
		if (request.homeCity() != null) {
			if (request.homeCity().isBlank()) {
				user.setHomeCity(null);
			} else {
				setHomeCity(user, request.homeCity());
			}
		}
		if (request.addRoles() != null) {
			user.getRoles().addAll(request.addRoles());
		}
		return changed(user);
	}
	
	private MeController.MeUpdate changed(User user) {
		User saved = users.save(user);
		return new MeController.MeUpdate(userViews.of(saved), auth.accessToken(saved));
	}
	
	private User load(UUID publicId) {
		return users.findByPublicId(publicId)
			.filter(u -> u.getStatus() != UserStatus.DELETED)
			.orElseThrow(() -> ApiException.notFound("User"));
	}
	
	private void setHomeCity(User user, String code) {
		String normalised = code.trim().toUpperCase(Locale.ROOT);
		cityService.requireLive(normalised);
		user.setHomeCity(cities.getReferenceById(normalised));
	}
	
	/** Users pick TENANT and/or LANDLORD only; ADMIN is never self-assigned (D-19). */
	private static void requireSelfServiceRoles(List<Role> roles, String field) {
		if (roles.stream().anyMatch(r -> r != Role.TENANT && r != Role.LANDLORD)) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError(field, "role_not_allowed",
				"Pick TENANT, LANDLORD or both.")));
		}
	}
	
}
