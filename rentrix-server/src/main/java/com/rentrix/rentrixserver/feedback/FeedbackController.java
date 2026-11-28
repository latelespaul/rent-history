package com.rentrix.rentrixserver.feedback;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /feedback}: the "Contact us" form, open to everyone and rate limited per IP. The answer is
 * {@code 204}; a filled honeypot ({@code website}, hidden from people) gets the same answer and is dropped.
 */
@RestController
@RequiredArgsConstructor
public class FeedbackController {
	
	/** {@code name} and {@code email} are optional (an email is needed only for a reply). */
	public record FeedbackRequest(@Size(max = 80) String name, @Email @Size(max = 320) String email,
		@NotNull FeedbackType type, @NotBlank @Size(max = FeedbackService.MESSAGE_MAX) String message,
		@Size(max = 200) String website) {
	}
	
	private final FeedbackRateLimiter limiter;
	private final FeedbackService feedback;
	
	@PostMapping("/feedback")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void send(@Valid @RequestBody FeedbackRequest body, HttpServletRequest request) {
		limiter.check(request);
		if (body.website() != null && !body.website().isBlank()) {
			return; // a bot filled the hidden field: answer as usual, keep and send nothing
		}
		feedback.receive(body.name(), body.email(), body.type(), body.message());
	}
	
}
