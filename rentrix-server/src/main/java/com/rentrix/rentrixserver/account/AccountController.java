package com.rentrix.rentrixserver.account;

import com.rentrix.rentrixserver.auth.RefreshCookies;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** {@code DELETE /me} (D-58, D-157): delete the caller's own account. */
@RestController
@RequiredArgsConstructor
public class AccountController {
	
	private final AccountDeletionService deletion;
	
	/** {@code confirm} must be the word {@code DELETE}. */
	public record DeleteAccountRequest(@Size(max = 16) String confirm) {
	}
	
	/** {@code 204}, and the refresh cookie is cleared: the session ends with the account. */
	@DeleteMapping("/me")
	ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller,
		@Valid @RequestBody(required = false) DeleteAccountRequest body, HttpServletRequest request) {
		deletion.delete(caller, body == null ? null : body.confirm());
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookies.clear(request)).build();
	}
	
}
