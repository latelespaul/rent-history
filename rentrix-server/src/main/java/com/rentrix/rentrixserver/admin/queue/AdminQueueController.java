package com.rentrix.rentrixserver.admin.queue;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The approvals queue and its answers (D-134, D-136, §12.2). {@code /admin/**} answers only on the admin host and only
 * to an admin console session (§13.3). Users are answered through {@code /admin/applications/{id}} (B11).
 */
@RestController
@RequiredArgsConstructor
public class AdminQueueController {
	
	private final AdminQueueService queue;
	
	/** Approve: {@code note} is optional. */
	public record ApproveRequest(@Size(max = 1000) String note) {
	}
	
	/** Reject or hide: {@code code} is one of the card's {@code rejectCodes}; {@code note} is optional. */
	public record DecisionRequest(@NotNull @Size(max = 40) String code, @Size(max = 1000) String note) {
	}
	
	/** Only {@code {"live": true}} for now: approving a city someone typed (D-143). */
	public record CityPatch(Boolean live) {
	}
	
	public enum ReportOutcome {
		DISMISS, HIDE
	}
	
	/** {@code HIDE} hides the reported review (abuse, D-51) and needs {@code code}; {@code DISMISS} keeps it. */
	public record ResolveRequest(@NotNull ReportOutcome outcome, @Size(max = 40) String code,
		@Size(max = 1000) String note) {
	}
	
	@GetMapping("/admin/queue")
	QueueView queue(@RequestParam(required = false) QueueType type, @RequestParam(required = false) String city) {
		return queue.queue(type, city);
	}
	
	@PostMapping("/admin/properties/{id}/approve")
	AdminQueueService.Decided approveProperty(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody(required = false) ApproveRequest body) {
		return approve(caller, QueueType.PROPERTY, id.toString(), body);
	}
	
	@PostMapping("/admin/properties/{id}/reject")
	AdminQueueService.Decided rejectProperty(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody DecisionRequest body) {
		return answer(caller, QueueType.PROPERTY, id.toString(), QueueAction.REJECT, body);
	}
	
	@PostMapping("/admin/tenancies/{id}/approve")
	AdminQueueService.Decided approveTenancy(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody(required = false) ApproveRequest body) {
		return approve(caller, QueueType.TENANCY, id.toString(), body);
	}
	
	@PostMapping("/admin/tenancies/{id}/reject")
	AdminQueueService.Decided rejectTenancy(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody DecisionRequest body) {
		return answer(caller, QueueType.TENANCY, id.toString(), QueueAction.REJECT, body);
	}
	
	@PostMapping("/admin/reviews/{id}/approve")
	AdminQueueService.Decided approveReview(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody(required = false) ApproveRequest body) {
		return approve(caller, QueueType.REVIEW, id.toString(), body);
	}
	
	@PostMapping("/admin/reviews/{id}/reject")
	AdminQueueService.Decided rejectReview(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody DecisionRequest body) {
		return answer(caller, QueueType.REVIEW, id.toString(), QueueAction.REJECT, body);
	}
	
	@PostMapping("/admin/reviews/{id}/hide")
	AdminQueueService.Decided hideReview(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody DecisionRequest body) {
		return answer(caller, QueueType.REVIEW, id.toString(), QueueAction.HIDE, body);
	}
	
	/** A PAN, masked Aadhaar or LinkedIn check on its own, also with no application (follow-up of D-144). */
	@PostMapping("/admin/identity/{id}/verify")
	AdminQueueService.Decided verifyIdentity(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody(required = false) ApproveRequest body) {
		return approve(caller, QueueType.IDENTITY, id.toString(), body);
	}
	
	@PostMapping("/admin/identity/{id}/reject")
	AdminQueueService.Decided rejectIdentity(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody DecisionRequest body) {
		return answer(caller, QueueType.IDENTITY, id.toString(), QueueAction.REJECT, body);
	}
	
	@PatchMapping("/admin/cities/{code}")
	AdminQueueService.Decided patchCity(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String code,
		@RequestBody CityPatch body) {
		if (!Boolean.TRUE.equals(body.live())) {
			throw ApiException.rule("action_not_allowed", "Only {\"live\": true} is supported here yet.");
		}
		return queue.decide(caller.publicId(), QueueType.CITY, code, QueueAction.APPROVE, null, null);
	}
	
	@PostMapping("/admin/reports/{id}/resolve")
	AdminQueueService.Decided resolveReport(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
		@Valid @RequestBody ResolveRequest body) {
		QueueAction action = body.outcome() == ReportOutcome.HIDE ? QueueAction.HIDE : QueueAction.APPROVE;
		return queue.decide(caller.publicId(), QueueType.REPORT, id.toString(), action, body.code(), body.note());
	}
	
	private AdminQueueService.Decided approve(AuthenticatedUser caller, QueueType type, String id, ApproveRequest body) {
		return queue.decide(caller.publicId(), type, id, QueueAction.APPROVE, null, body == null ? null : body.note());
	}
	
	private AdminQueueService.Decided answer(AuthenticatedUser caller, QueueType type, String id, QueueAction action,
		DecisionRequest body) {
		return queue.decide(caller.publicId(), type, id, action, body.code(), body.note());
	}
	
}
