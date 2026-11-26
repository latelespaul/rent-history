package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The welcome email on first sign-up (D-172), once per user: the {@code welcome} row in {@code mail_sends} is
 * written in the sign-up's own transaction, and {@link EmailSender#send} sends after it commits, so a slow or
 * failing mail provider never blocks or fails the sign-up.
 */
@Component
@RequiredArgsConstructor
public class WelcomeMailer {
	
	public static final String CAMPAIGN = "welcome";
	
	private final MailSendRepository sends;
	private final MailMessages messages;
	private final EmailSender email;
	
	/** Call it right after the new user is saved. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void signedUp(User user) {
		if (user.getEmail() == null || user.isDemo() || user.getMailOptOutAt() != null
			|| sends.existsByUserAndCampaign(user, CAMPAIGN)) {
			return;
		}
		sends.save(new MailSend(user, CAMPAIGN));
		email.send(messages.welcome(new MailMessages.Recipient(user.getPublicId(), user.getEmail(),
			user.getDisplayName())));
	}
	
}
