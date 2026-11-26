package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.notification.email.ReviewReminders;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /admin/mail/review-reminder} (D-172): emails every user, one by one (never BCC), a short "rate your
 * landlord" reminder with an unsubscribe link. {@code onlyWithoutReview} (default {@code true}) skips users who have
 * written a review; {@code dryRun} (default {@code true}) only counts the recipients. Opted-out users and users
 * already mailed this campaign are always skipped. Answers counts only, never email addresses. {@code /admin/**}
 * answers only on the admin host and only to an admin console session (§13.3); membership is re-checked (D-19).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminMailController {
	
	/**
	 * @param campaign {@code review-reminder} by default; {@code review-reminder-<tag>} starts a new round that mails
	 *                 users again
	 */
	public record ReminderRequest(Boolean onlyWithoutReview, Boolean dryRun,
		@Pattern(regexp = "review-reminder(-[a-z0-9]{1,20})?") String campaign) {
	}
	
	private final ReviewReminders reminders;
	private final UserRepository users;
	private final AdminConsoleMembers members;
	
	@PostMapping("/admin/mail/review-reminder")
	public ReviewReminders.Result reviewReminder(@AuthenticationPrincipal AuthenticatedUser caller,
		@Valid @RequestBody(required = false) ReminderRequest body) {
		users.findByPublicId(caller.publicId()).filter(members::isMember)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "forbidden", "You are not allowed to do this."));
		ReminderRequest request = body == null ? new ReminderRequest(null, null, null) : body;
		String campaign = request.campaign() == null ? ReviewReminders.CAMPAIGN : request.campaign();
		boolean dryRun = !Boolean.FALSE.equals(request.dryRun());
		ReviewReminders.Result result = reminders.run(campaign,
			!Boolean.FALSE.equals(request.onlyWithoutReview()), dryRun);
		if (!dryRun) {
			log.info("Admin {} sent review reminder '{}': {} sent, {} failed", caller.publicId(), campaign,
				result.sent(), result.failed());
		}
		return result;
	}
	
}
