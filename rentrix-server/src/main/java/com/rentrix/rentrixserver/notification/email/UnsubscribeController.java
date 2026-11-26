package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.config.BrandProperties;
import com.rentrix.rentrixserver.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * The unsubscribe link of every welcome and reminder email (D-172), anonymous: {@code GET} (the link, one click)
 * and {@code POST} (the mail app's one-click unsubscribe, RFC 8058) set {@code users.mail_opt_out_at} for the user
 * the signed token names and answer a small HTML page. A forged or broken token changes nothing ({@code 400}).
 */
@RestController
public class UnsubscribeController {
	
	private final UnsubscribeTokens tokens;
	private final UserRepository users;
	private final Clock clock;
	private final String brand;
	private final String webBaseUrl;
	
	public UnsubscribeController(UnsubscribeTokens tokens, UserRepository users, Clock clock, BrandProperties brand,
		@Value("${app.web.base-url:}") String webBaseUrl) {
		this.tokens = tokens;
		this.users = users;
		this.clock = clock;
		this.brand = brand.name();
		this.webBaseUrl = MailProperties.stripSlash(webBaseUrl);
	}
	
	@GetMapping(value = "/mail/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
	@Transactional
	public ResponseEntity<String> unsubscribeLink(@RequestParam(required = false) String token) {
		return unsubscribe(token);
	}
	
	@PostMapping(value = "/mail/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
	@Transactional
	public ResponseEntity<String> oneClick(@RequestParam(required = false) String token) {
		return unsubscribe(token);
	}
	
	private ResponseEntity<String> unsubscribe(String token) {
		Optional<UUID> publicId = tokens.verify(token);
		if (publicId.isEmpty()) {
			return page(HttpStatus.BAD_REQUEST, "This link does not work",
				"The unsubscribe link is broken. Open it again from the email, or contact us.");
		}
		users.findByPublicId(publicId.get()).filter(user -> user.getMailOptOutAt() == null)
			.ifPresent(user -> user.setMailOptOutAt(clock.instant().truncatedTo(ChronoUnit.MILLIS)));
		return page(HttpStatus.OK, "You are unsubscribed",
			brand + " will not send you welcome or reminder emails any more. Emails about your own tenancies "
				+ "and reviews still arrive.");
	}
	
	private ResponseEntity<String> page(HttpStatus status, String title, String text) {
		String html = """
			<!doctype html>
			<html lang="en">
			<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
			<title>%s</title></head>
			<body style="font-family:system-ui,sans-serif;max-width:32rem;margin:3rem auto;padding:0 1rem;\
			color:#1f2933;line-height:1.5">
			<h1 style="font-size:1.25rem">%s</h1>
			<p>%s</p>
			<p><a href="%s/">Go to %s</a></p>
			</body>
			</html>
			""".formatted(escape(title), escape(title), escape(text), escape(webBaseUrl), escape(brand));
		return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
	}
	
	private static String escape(String text) {
		return HtmlUtils.htmlEscape(text, "UTF-8");
	}
	
}
