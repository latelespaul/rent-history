package com.rentrix.rentrixserver.invite;

import com.rentrix.rentrixserver.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own invite code and friends-joined count (invite-friends §4); the only route that returns either. */
@RestController
@RequestMapping("/me/invite")
@RequiredArgsConstructor
public class InviteController {
	
	private final InviteService invites;
	
	@GetMapping
	InviteService.InviteView get(@AuthenticationPrincipal AuthenticatedUser caller) {
		return invites.mine(caller.publicId());
	}
	
}
