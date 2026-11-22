package com.rentrix.rentrixserver.user.profile;

import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.review.ReviewController;
import com.rentrix.rentrixserver.review.dto.ReviewView;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Person profiles (§12.2 "Users"): signed-in callers only; visibility per D-54 and D-55. */
@RestController
@RequiredArgsConstructor
public class UserProfileController {
	
	private final UserProfileService profiles;
	
	@GetMapping("/users/{id}")
	public UserProfileView get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return profiles.get(id, caller);
	}
	
	/** {@code GET /users/{id}/reviews?as=LANDLORD|TENANT&page=&size=&sort=newest|rating}. */
	@GetMapping("/users/{id}/reviews")
	public PageResponse<ReviewView> reviews(@PathVariable UUID id,
		@RequestParam(required = false, name = "as") TenancyParty as, @RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return profiles.reviews(id, as, caller,
			PageRequests.of(page, size, sort, ReviewController.SORTS, ReviewController.DEFAULT_SORT));
	}
	
}
