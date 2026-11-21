package com.rentrix.rentrixserver.support;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.idempotency.Idempotent;
import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only endpoints (test sources only) that exercise the cross-cutting web layer. */
@Slf4j
@RestController
@RequestMapping("/test-only")
class TestOnlyController {
	
	/** How many times the idempotent write really ran. */
	static final AtomicInteger WRITES = new AtomicInteger();
	
	record EchoRequest(@NotBlank @Size(max = 10) String name, @Min(1) @Max(5) int rating) {
	}
	
	@PostMapping("/idempotent")
	@Idempotent
	ResponseEntity<Map<String, Object>> idempotentWrite(@Valid @RequestBody EchoRequest request) {
		if (request.name().equals("fail")) {
			throw ApiException.rule("test_rule_broken", "The write failed.");
		}
		int count = WRITES.incrementAndGet();
		return ResponseEntity.created(URI.create("/test-only/items/" + count))
			.body(Map.of("write", count, "name", request.name()));
	}
	
	@PostMapping("/not-idempotent")
	Map<String, Object> plainWrite(@RequestBody EchoRequest request) {
		return Map.of("write", WRITES.incrementAndGet());
	}
	
	@GetMapping("/conflict")
	void conflict() {
		throw ApiException.conflict("test_conflict", "Already exists.");
	}
	
	@GetMapping("/rate-limited")
	void rateLimited() {
		throw ApiException.rateLimited(Duration.ofMillis(1500));
	}
	
	@GetMapping("/boom")
	void boom() {
		throw new IllegalStateException("internal secret detail");
	}
	
	@GetMapping("/log")
	Map<String, String> logSomething() {
		log.info("structured log probe");
		return Map.of("ok", "yes");
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
