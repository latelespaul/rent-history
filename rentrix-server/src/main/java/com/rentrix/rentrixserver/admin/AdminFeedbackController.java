package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.feedback.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /admin/feedback?page=&size=}: "Contact us" messages, newest first. Admin host and admin console session only. */
@RestController
@RequiredArgsConstructor
public class AdminFeedbackController {
	
	private final FeedbackService feedback;
	
	@GetMapping("/admin/feedback")
	public PageResponse<FeedbackService.FeedbackEntry> list(@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size) {
		return feedback.list(page, size);
	}
	
}
