package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.agreement.dto.AdminAgreementView;
import com.rentrix.rentrixserver.agreement.dto.SignedUrlView;
import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rental agreements on the admin console (§12.2 admin, D-130, D-136, D-151): the agreements waiting for a check and
 * the file through a signed link; the tenancy is decided on its approvals-queue card. {@code /admin/**} is admin-host
 * and {@code ADMIN} only.
 */
@RestController
@RequestMapping("/admin/agreements")
@RequiredArgsConstructor
public class AdminAgreementController {
	
	static final Map<String, String> SORTS = Map.of("oldest", "createdAt");
	static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");
	
	private final AdminAgreementService agreements;
	
	/** {@code GET /admin/agreements?status=SUBMITTED,DECIDED&page=&size=}: oldest first. */
	@GetMapping
	public PageResponse<AdminAgreementView> list(@RequestParam(required = false) List<AgreementStatus> status,
		@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		Set<AgreementStatus> statuses = status == null || status.isEmpty() ? Set.of() : EnumSet.copyOf(status);
		return agreements.list(statuses, PageRequests.of(page, size, null, SORTS, DEFAULT_SORT));
	}
	
	@GetMapping("/{id}")
	public AdminAgreementView get(@PathVariable UUID id) {
		return agreements.get(id);
	}
	
	@GetMapping("/{id}/url")
	public SignedUrlView url(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return agreements.url(id, caller);
	}
	
}
