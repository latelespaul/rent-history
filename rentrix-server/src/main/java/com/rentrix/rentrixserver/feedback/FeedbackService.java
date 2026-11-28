package com.rentrix.rentrixserver.feedback;

import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.config.BrandProperties;
import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.notification.email.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps "Contact us" messages and tells the team by email ({@code FEEDBACK_TO}, default the public
 * {@code hello@} address). Storing is what counts: the email is best effort, sent after the commit through
 * {@link EmailSender}, and a failure (or mail being off) is only logged, never the visitor's problem.
 */
@Slf4j
@Service
public class FeedbackService {
	
	/** Longest message the form and the API take. */
	public static final int MESSAGE_MAX = 2000;
	
	private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
	
	private final FeedbackRepository repository;
	private final EmailSender email;
	private final String brand;
	private final String to;
	
	public FeedbackService(FeedbackRepository repository, EmailSender email, BrandProperties brand,
		@Value("${app.feedback.to:hello@doorwayliving.in}") String to) {
		this.repository = repository;
		this.email = email;
		this.brand = brand.name();
		this.to = to;
	}
	
	/** One message as the admin console lists it. */
	public record FeedbackEntry(String name, String email, FeedbackType type, String message, Instant createdAt) {
		
		static FeedbackEntry of(Feedback row) {
			return new FeedbackEntry(row.getName(), row.getEmail(), row.getType(), row.getMessage(),
				row.getCreatedAt());
		}
		
	}
	
	@Transactional
	public void receive(String name, String email, FeedbackType type, String message) {
		Feedback saved = repository.save(new Feedback(clean(name), clean(email), type, message.strip()));
		notifyTeam(saved);
	}
	
	@Transactional(readOnly = true)
	public PageResponse<FeedbackEntry> list(Integer page, Integer size) {
		return PageResponse.of(repository.findAll(PageRequests.of(page, size, null, Map.of(), NEWEST)),
			FeedbackEntry::of);
	}
	
	private void notifyTeam(Feedback row) {
		String type = row.getType().name().toLowerCase(Locale.ROOT);
		String from = row.getName() == null ? "someone" : row.getName();
		String reply = row.getEmail() == null ? "No email given, so no reply is possible." : "Reply to: " + row.getEmail();
		String body = """
			New %s from %s on %s.
			
			%s
			
			%s""".formatted(type, from, brand, row.getMessage(), reply);
		try {
			email.send(new EmailMessage(to, brand + " " + type + ": " + subject(row), body));
		}
		catch (RuntimeException e) {
			log.warn("Feedback email not sent: {}", e.getClass().getSimpleName());
		}
	}
	
	/** The first words of the message, on one line. */
	private static String subject(Feedback row) {
		String line = row.getMessage().replaceAll("\\s+", " ");
		return line.length() <= 60 ? line : line.substring(0, 57) + "...";
	}
	
	private static String clean(String value) {
		return StringUtils.hasText(value) ? value.strip() : null;
	}
	
}
