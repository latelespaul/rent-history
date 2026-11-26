package com.rentrix.rentrixserver.metrics;

import com.rentrix.rentrixserver.common.error.ApiException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limit on {@code POST /metrics/pageview}: 60 requests per minute per client IP, in memory (Bucket4j), like
 * the sign-in limit (§13). The IP is only a map key here; it is dropped with idle buckets and never stored.
 */
@Component
class PageViewRateLimiter {
	
	static final int REQUESTS = 60;
	static final Duration PERIOD = Duration.ofMinutes(1);
	static final int MAX_TRACKED_CLIENTS = 10_000;
	
	private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
	
	/** @throws ApiException {@code 429 rate_limited} when the caller's IP used up its allowance */
	void check(HttpServletRequest request) {
		if (buckets.size() > MAX_TRACKED_CLIENTS) {
			buckets.values().removeIf(b -> b.getAvailableTokens() >= REQUESTS);
		}
		Bucket bucket = buckets.computeIfAbsent(String.valueOf(request.getRemoteAddr()), ip -> Bucket.builder()
			.addLimit(limit -> limit.capacity(REQUESTS).refillGreedy(REQUESTS, PERIOD)).build());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			throw ApiException.rateLimited(Duration.ofNanos(probe.getNanosToWaitForRefill()));
		}
	}
	
}
