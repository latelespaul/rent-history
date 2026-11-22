package com.rentrix.rentrixserver.support;

import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.notification.email.EmailSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test mail box: keeps every email so tests can read invite links instead of parsing logs. */
@Primary
@Component
public class RecordingEmailSender implements EmailSender {
	
	private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
	
	@Override
	public void send(EmailMessage message) {
		sent.add(message);
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
