package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * {@code GET /admin/audit-log?action=&page=&size=}: the append-only audit log (D-87), newest first, optionally one
 * action, e.g. {@code ACCOUNT_DELETED} (D-159). Admin host and admin console session only (§13.3). An account
 * deletion row carries the user id and time only; its actor is the user themself, shown as {@code self}.
 */
@RestController
@RequiredArgsConstructor
public class AdminAuditLogController {
	
	private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
	
	private final ModerationActionRepository actions;
	
	/**
	 * One audit row. {@code actor} is the admin's display name, or {@code null} with {@code self = true} for the
	 * user's own account deletion.
	 */
	public record AuditEntry(ModerationActionType action, ModerationTargetType targetType, String targetRef,
		String actor, boolean self, String reason, Instant createdAt) {
		
		static AuditEntry of(ModerationAction row) {
			boolean self = row.getAction() == ModerationActionType.ACCOUNT_DELETED;
			return new AuditEntry(row.getAction(), row.getTargetType(), row.getTargetRef(),
				self ? null : row.getAdmin().getDisplayName(), self, row.getReason(), row.getCreatedAt());
		}
		
	}
	
	@GetMapping("/admin/audit-log")
	@Transactional(readOnly = true)
	public PageResponse<AuditEntry> list(@RequestParam(required = false) ModerationActionType action,
		@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		Pageable pageable = PageRequests.of(page, size, null, Map.of(), NEWEST);
		Page<ModerationAction> rows = action == null ? actions.findAll(pageable) : actions.findByAction(action, pageable);
		return PageResponse.of(rows, AuditEntry::of);
	}
	
}
