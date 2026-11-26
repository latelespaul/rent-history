package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The admin "send review reminder" (D-172): one email per user, never BCC, to every user who may get mail
 * ({@link MailSendRepository#campaignRecipients}), by default only those who have not written a review yet. Each
 * delivered email is recorded, so a second run mails only who was missed or failed. Emails go one
 * by one with {@code app.mail.send-interval} between them, under Resend's per-second limit. One run at a time.
 */
@Slf4j
@Service
public class ReviewReminders {
	
	public static final String CAMPAIGN = "review-reminder";
	
	/** Counts only: the result never holds an email address. */
	public record Result(boolean dryRun, boolean mailEnabled, int recipients, int sent, int failed) {
	}
	
	private final MailSendRepository sends;
	private final UserRepository users;
	private final MailMessages messages;
	private final EmailSender email;
	private final Duration interval;
	private final TransactionTemplate transactions;
	private final TransactionTemplate readOnly;
	private final AtomicBoolean running = new AtomicBoolean();
	
	public ReviewReminders(MailSendRepository sends, UserRepository users, MailMessages messages, EmailSender email,
		MailProperties properties, PlatformTransactionManager transactionManager) {
		this.sends = sends;
		this.users = users;
		this.messages = messages;
		this.email = email;
		this.interval = properties.sendInterval();
		this.transactions = new TransactionTemplate(transactionManager);
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
	}
	
	/**
	 * @param onlyWithoutReview skip users who have written a review
	 * @param dryRun            count the recipients, send nothing
	 */
	public Result run(boolean onlyWithoutReview, boolean dryRun) {
		if (dryRun) {
			return new Result(true, email.delivers(), recipients(onlyWithoutReview).size(), 0, 0);
		}
		if (!email.delivers()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "mail_disabled",
				"Mail is off on this server (no RESEND_API_KEY), so nothing can be sent.");
		}
		if (!running.compareAndSet(false, true)) {
			throw ApiException.conflict("reminder_running", "A reminder is being sent right now. Try again later.");
		}
		try {
			return send(recipients(onlyWithoutReview));
		} finally {
			running.set(false);
		}
	}
	
	private List<MailMessages.Recipient> recipients(boolean onlyWithoutReview) {
		return readOnly.execute(status -> (onlyWithoutReview
			? sends.campaignRecipientsWithoutReview(CAMPAIGN, UnclaimedLandlord.EMAIL)
			: sends.campaignRecipients(CAMPAIGN, UnclaimedLandlord.EMAIL)).stream()
			.map(user -> new MailMessages.Recipient(user.getPublicId(), user.getEmail(), user.getDisplayName()))
			.toList());
	}
	
	private Result send(List<MailMessages.Recipient> recipients) {
		int sent = 0;
		int failed = 0;
		for (int i = 0; i < recipients.size(); i++) {
			if (i > 0 && !pause()) {
				break;
			}
			MailMessages.Recipient to = recipients.get(i);
			try {
				email.sendNow(messages.reviewReminder(to));
			} catch (EmailSendException e) {
				failed++;
				log.warn("Review reminder to {} not sent: {}", LogEmailSender.mask(to.email()), e.getMessage());
				continue;
			}
			transactions.executeWithoutResult(status -> {
				User user = users.findByPublicId(to.publicId()).orElseThrow();
				sends.save(new MailSend(user, CAMPAIGN));
			});
			sent++;
		}
		log.info("Review reminder: {} recipients, {} sent, {} failed", recipients.size(), sent, failed);
		return new Result(false, true, recipients.size(), sent, failed);
	}
	
	/** Waits {@link #interval}; false when the thread was interrupted (the run stops). */
	private boolean pause() {
		if (interval.isZero() || interval.isNegative()) {
			return true;
		}
		try {
			Thread.sleep(interval);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
	
}
