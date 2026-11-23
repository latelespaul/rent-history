package com.rentrix.rentrixserver.admin.queue;

import com.rentrix.rentrixserver.verification.VerificationState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One approvals-queue card (D-136): enough to decide on a phone without opening anything else.
 *
 * @param id      the item's {@code public_id} (a city's code for {@code CITY})
 * @param details short lines of key facts, already masked like everywhere else (D-57, D-106)
 * @param verificationStatus {@code UNVERIFIED} for everything waiting; a decided item leaves the queue
 * @param link    a main-site path that shows the item publicly, or null
 * @param actions what the admin can do with it
 * @param rejectCodes the reason codes a reject or hide takes for this type
 * @param agreementId {@code TENANCY} only: the tenancy's live rental agreement (D-123), whose file the admin opens
 *                    through {@code GET /admin/agreements/{id}/url}; null when none was uploaded
 */
public record QueueItem(QueueType type, String id, String title, String subtitle, List<String> details,
	List<String> flags, String city, Instant createdAt, VerificationState verificationStatus, String link,
	List<QueueAction> actions, List<String> rejectCodes, UUID agreementId) {
}
