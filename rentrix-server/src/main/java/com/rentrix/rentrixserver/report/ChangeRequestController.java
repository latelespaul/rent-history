package com.rentrix.rentrixserver.report;

import com.rentrix.rentrixserver.report.dto.ChangeRequest;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** "Ask our team to change this" on the caller's own tenancy, home or review (D-156). */
@RestController
@RequiredArgsConstructor
public class ChangeRequestController {
	
	private final ChangeRequestService changeRequests;
	
	/** {@code POST /me/change-requests}: files an open request in the admin report queue. */
	@PostMapping("/me/change-requests")
	public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody ChangeRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		UUID id = changeRequests.create(request, caller);
		return ResponseEntity.status(201).body(Map.of("id", id));
	}
	
}
