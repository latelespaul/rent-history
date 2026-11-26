package com.rentrix.rentrixserver.support;

import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.notification.email.EmailSendException;
import com.rentrix.rentrixserver.notification.email.EmailSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test mail box: keeps every email so tests can read invite links instead of parsing logs. It counts as a real
 * sender ({@link #delivers()}), and {@link #failFor} makes {@link #sendNow} refuse one address.
 */
@Primary
@Component
public class RecordingEmailSender implements EmailSender {
	
	private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
	private final Set<String> failing = ConcurrentHashMap.newKeySet();
	
	@Override
	public void send(EmailMessage message) {
		sent.add(message);
	}
	
	@Override
	public void sendNow(EmailMessage message) {
		if (failing.contains(message.to().toLowerCase(Locale.ROOT))) {
			throw new EmailSendException("Test provider refused the email");
		}
		sent.add(message);
	}
	
	@Override
	public boolean delivers() {
		return true;
	}
	
	/** {@link #sendNow} throws for {@code to} from now on. */
	public void failFor(String to) {
		failing.add(to.toLowerCase(Locale.ROOT));
	}
	
	/** Ends {@link #failFor}. */
	public void stopFailing(String to) {
		failing.remove(to.toLowerCase(Locale.ROOT));
	}
	
	/** Emails to {@code to}, oldest first. */
	public List<EmailMessage> to(String to) {
		return sent.stream().filter(m -> m.to().equalsIgnoreCase(to)).toList();
	}
	
	public EmailMessage last(String to) {
		List<EmailMessage> messages = to(to);
		if (messages.isEmpty()) {
			throw new AssertionError("No email to " + to);
		}
		return messages.getLast();
	}
	
}
