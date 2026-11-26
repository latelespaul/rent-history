package com.rentrix.rentrixserver.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /metrics/pageview}: anonymous, rate limited per IP, a tiny body (D-174). No cookie is read or set and
 * the answer is always {@code 204}; see {@link TrafficService} for what is kept.
 */
@RestController
@RequiredArgsConstructor
public class PageViewController {
	
	/** {@code source} is only {@code invite}, a {@code utm_source} or {@code internal}; never a ref code. */
	public record PageViewRequest(@Size(max = 200) String path, @Size(max = 300) String referrer,
		@Size(max = 40) String source) {
	}
	
	private final PageViewRateLimiter limiter;
	private final TrafficService traffic;
	
	@PostMapping("/metrics/pageview")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void pageView(@Valid @RequestBody PageViewRequest body, HttpServletRequest request) {
		limiter.check(request);
		traffic.record(TrafficNormaliser.path(body.path()),
			TrafficNormaliser.source(body.source(), body.referrer(), request.getServerName()),
			request.getRemoteAddr(), request.getHeader("User-Agent"));
	}
	
}
