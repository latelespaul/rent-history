package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The caller's own identity checks (§12.2 "Me", D-99 – D-106): read them masked, verify a phone by SMS code or with
 * Truecaller ({@link TruecallerService}), add
 * or remove the optional LinkedIn profile. PAN and masked Aadhaar ({@code POST /me/identity/pan}, {@code /aadhaar})
 * come with PLAN B12, because they need private document uploads.
 */
@RestController
@RequestMapping("/me/identity")
@RequiredArgsConstructor
public class IdentityController {
	
	private final IdentityService identity;
	private final PhoneOtpService phoneOtp;
	private final TruecallerService truecaller;
	private final UserRepository users;
	
	public record PhoneStartRequest(@NotBlank @Size(max = 20) String phone) {
	}
	
	public record PhoneConfirmRequest(@NotNull UUID challengeId, @NotBlank @Size(max = 6) String code) {
	}
	
	public record LinkedinRequest(@NotBlank @Size(max = 400) String url) {
	}
	
	@GetMapping
	@Transactional(readOnly = true)
	public IdentityView get(@AuthenticationPrincipal AuthenticatedUser caller) {
		return identity.view(load(caller));
	}
	
	@PostMapping("/phone/start")
	public PhoneOtpService.Started startPhone(@AuthenticationPrincipal AuthenticatedUser caller,
		@Valid @RequestBody PhoneStartRequest body) {
		return phoneOtp.start(caller.publicId(), body.phone());
	}
	
	@PostMapping("/phone/confirm")
	public IdentityView confirmPhone(@AuthenticationPrincipal AuthenticatedUser caller,
		@Valid @RequestBody PhoneConfirmRequest body) {
		return phoneOtp.confirm(caller.publicId(), body.challengeId(), body.code());
	}
	
	/** A Truecaller request: open {@code deepLink} on the phone, then poll {@code GET /truecaller/{requestId}}. */
	@PostMapping("/truecaller/start")
	public TruecallerService.Started startTruecaller(@AuthenticationPrincipal AuthenticatedUser caller) {
		return truecaller.start(caller.publicId());
	}
	
	@GetMapping("/truecaller/{requestId}")
	public TruecallerService.RequestView truecallerStatus(@AuthenticationPrincipal AuthenticatedUser caller,
		@PathVariable String requestId) {
		return truecaller.status(caller.publicId(), requestId);
	}
	
	@PutMapping("/linkedin")
	@Transactional
	public IdentityView setLinkedin(@AuthenticationPrincipal AuthenticatedUser caller,
		@Valid @RequestBody LinkedinRequest body) {
		String url = LinkedinUrls.normalise(body.url()).orElseThrow(() -> ApiException.validation(List.of(
			new ApiProblem.FieldError("url", "pattern", "Use your LinkedIn profile link, e.g. https://www.linkedin.com/in/your-name."))));
		return identity.setLinkedin(load(caller), url);
	}
	
	@DeleteMapping("/linkedin")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void removeLinkedin(@AuthenticationPrincipal AuthenticatedUser caller) {
		identity.removeLinkedin(load(caller));
	}
	
	private User load(AuthenticatedUser caller) {
		return users.findByPublicId(caller.publicId())
			.filter(u -> u.getStatus() != UserStatus.DELETED)
			.orElseThrow(() -> ApiException.notFound("User"));
	}
	
}
