package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The caller's own verification per held role (§12.2, D-89, D-90). */
@RestController
@RequestMapping("/me/verification")
@RequiredArgsConstructor
public class VerificationController {
	
	private final VerificationService verification;
	
	@GetMapping
	List<VerificationView> get(@AuthenticationPrincipal AuthenticatedUser caller) {
		return verification.view(caller.publicId());
	}
	
	/** {@code role} is {@code TENANT} or {@code LANDLORD} (any case). */
	@PostMapping("/{role}/submit")
	VerificationView submit(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String role) {
		return verification.submit(caller.publicId(), role);
	}
	
}
