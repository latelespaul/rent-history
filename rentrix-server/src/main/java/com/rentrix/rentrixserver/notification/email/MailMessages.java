package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.config.BrandProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The welcome and review-reminder emails (D-172): short plain text plus simple HTML, one "Rate your landlord" link
 * to {@code /homes/new} on the web app ({@code APP_BASE_URL}) and the unsubscribe link on the API
 * ({@code APP_API_BASE_URL}). They hold only the recipient's own display name: never another user's data, reviews
 * or stay dates.
 */
@Component
public class MailMessages {
	
	/** The web path where a tenant adds their home and reviews it (D-139). */
	static final String WRITE_REVIEW_PATH = "/homes/new";
	
	private final String brand;
	private final String webBaseUrl;
	private final String apiBaseUrl;
	private final UnsubscribeTokens tokens;
	
	public MailMessages(BrandProperties brand, @Value("${app.web.base-url:}") String webBaseUrl,
		MailProperties properties, UnsubscribeTokens tokens) {
		this.brand = brand.name();
		this.webBaseUrl = MailProperties.stripSlash(webBaseUrl);
		this.apiBaseUrl = properties.apiBaseUrl() == null ? this.webBaseUrl + "/api/v1" : properties.apiBaseUrl();
		this.tokens = tokens;
	}
	
	/** A recipient: the user's public id (for the unsubscribe token), email and display name. */
	public record Recipient(UUID publicId, String email, String displayName) {
	}
	
	public EmailMessage welcome(Recipient to) {
		return message(to, "Welcome to " + brand,
			"Thanks for joining " + brand + ". Here tenants and landlords rate each other, so the next person "
				+ "knows who they are dealing with.",
			"Rented a home? Rate your landlord. It takes two minutes:",
			"You get this email because you signed up.");
	}
	
	public EmailMessage reviewReminder(Recipient to) {
		return message(to, "Rate your landlord on " + brand,
			"A short review of your landlord helps the next tenant choose a home.",
			"It takes two minutes:",
			"You get this email because you have an account on " + brand + ".");
	}
	
	public String unsubscribeUrl(UUID publicId) {
		return apiBaseUrl + "/mail/unsubscribe?token="
			+ URLEncoder.encode(tokens.issue(publicId), StandardCharsets.UTF_8);
	}
	
	private EmailMessage message(Recipient to, String subject, String intro, String ask, String why) {
		String link = webBaseUrl + WRITE_REVIEW_PATH;
		String unsubscribe = unsubscribeUrl(to.publicId());
		String text = """
			Hi %s,
			
			%s
			
			%s
			%s
			
			%s
			
			%s Unsubscribe: %s""".formatted(to.displayName(), intro, ask, link, brand, why, unsubscribe);
		String html = """
			<p>Hi %s,</p>
			<p>%s</p>
			<p>%s</p>
			<p><a href="%s" style="display:inline-block;padding:10px 18px;background:#0f766e;color:#ffffff;\
			text-decoration:none;border-radius:8px;font-weight:600">Rate your landlord</a></p>
			<p>%s</p>
			<p style="color:#6b7280;font-size:12px">%s <a href="%s" style="color:#6b7280">Unsubscribe</a></p>
			""".formatted(escape(to.displayName()), escape(intro), escape(ask), escape(link), escape(brand),
			escape(why), escape(unsubscribe));
		return new EmailMessage(to.email(), subject, text, html, unsubscribe);
	}
	
	private static String escape(String text) {
		return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
	}
	
}
