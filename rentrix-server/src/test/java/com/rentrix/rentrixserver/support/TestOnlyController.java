package com.rentrix.rentrixserver.support;

import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only endpoints (test sources only) that exercise the cross-cutting web layer. */
@RestController
@RequestMapping("/test-only")
class TestOnlyController {
	
	record EchoRequest(@NotBlank @Size(max = 10) String name, @Min(1) @Max(5) int rating) {
	}
	
	@GetMapping("/me")
	Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user) {
		return Map.of("publicId", user.publicId().toString(), "roles", user.roles());
	}
	
	@GetMapping("/landlord-only")
	@PreAuthorize("hasRole('LANDLORD')")
	Map<String, String> landlordOnly() {
		return Map.of("ok", "yes");
	}
	
	@PostMapping("/echo")
	EchoRequest echo(@Valid @RequestBody EchoRequest request) {
		return request;
	}
	
	@GetMapping("/page")
	Map<String, Object> page(@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
		Pageable pageable = PageRequests.of(page, size, sort, Map.of("name", "displayName"), Sort.by("createdAt"));
		return Map.of("page", pageable.getPageNumber(), "size", pageable.getPageSize(),
			"sort", pageable.getSort().toString());
	}
	
}
