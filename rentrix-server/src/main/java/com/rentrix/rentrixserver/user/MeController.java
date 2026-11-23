package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The caller's own account (§12.2 "Me"). Account deletion ({@code DELETE /me}, D-58) is {@code AccountController}. */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {
	
	private final MeService me;
	
	/** First sign-in role pick (D-73): {@code roles} is TENANT, LANDLORD or both. */
	public record OnboardingRequest(@NotEmpty @Size(max = 2) List<Role> roles, @Size(max = 8) String homeCity,
		@AssertTrue(message = "Accept the terms to continue.") boolean acceptTerms) {
	}
	
	/**
	 * Profile edits and role adds (D-16). Absent fields are left as they are; an empty {@code phone} or
	 * {@code homeCity} clears it. Roles are only ever added.
	 */
	public record UpdateMeRequest(@Size(max = 60) String displayName, @Size(max = 20) String phone,
		@Size(max = 8) String homeCity, @Size(max = 2) List<Role> addRoles) {
	}
	
	/** A change to the account, with a fresh access token carrying the current roles (D-62, D-73). */
	public record MeUpdate(UserView user, String accessToken) {
	}
	
	@GetMapping
	UserView get(@AuthenticationPrincipal AuthenticatedUser caller) {
		return me.get(caller.publicId());
	}
	
	@PatchMapping
	MeUpdate update(@AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody UpdateMeRequest body) {
		return me.update(caller.publicId(), body);
	}
	
	@PostMapping("/onboarding")
	MeUpdate onboard(@AuthenticationPrincipal AuthenticatedUser caller, @Valid @RequestBody OnboardingRequest body) {
		return me.onboard(caller.publicId(), body);
	}
	
}
