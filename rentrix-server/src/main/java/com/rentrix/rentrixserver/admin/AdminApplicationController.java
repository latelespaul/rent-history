package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.identity.IdentityRejectCode;
import com.rentrix.rentrixserver.identity.IdentityStatus;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.verification.AdminApplicationView;
import com.rentrix.rentrixserver.verification.VerificationDecisionCode;
import com.rentrix.rentrixserver.verification.VerificationDecisions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin decisions on verification applications (§12.2 admin, D-87 – D-90, D-105); {@code /admin/**} is
 * {@code ADMIN} only. Every decision is audited; reject and revoke need a code and a note shown to the user.
 */
@RestController
@RequestMapping("/admin/applications/{id}")
@RequiredArgsConstructor
public class AdminApplicationController {
	
	private final VerificationDecisions decisions;
	
	/** One PAN or Aadhaar check decided with the approval; {@code REJECTED} needs a {@code code}. */
	public record IdentityResultRequest(@NotNull UUID id, @NotNull IdentityStatus result, IdentityRejectCode code,
		@Size(max = 1000) String note) {
	}
	
	public record ApproveRequest(@Size(max = 1000) String note,
		@Size(max = 4) List<@Valid @NotNull IdentityResultRequest> identity) {
	}
	
	public record DecisionRequest(@NotNull VerificationDecisionCode code, @NotBlank @Size(max = 1000) String note) {
	}
	
	@PostMapping("/approve")
	AdminApplicationView approve(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody(required = false) ApproveRequest body) {
		ApproveRequest request = body == null ? new ApproveRequest(null, null) : body;
		List<VerificationDecisions.IdentityResult> results = request.identity() == null ? List.of()
			: request.identity().stream()
				.map(r -> new VerificationDecisions.IdentityResult(r.id(), r.result(), r.code(), r.note()))
				.toList();
		return decisions.approve(caller.publicId(), id, request.note(), results);
	}
	
	@PostMapping("/reject")
	AdminApplicationView reject(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody DecisionRequest body) {
		return decisions.reject(caller.publicId(), id, body.code(), body.note());
	}
	
	@PostMapping("/revoke")
	AdminApplicationView revoke(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody DecisionRequest body) {
		return decisions.revoke(caller.publicId(), id, body.code(), body.note());
	}
	
}
