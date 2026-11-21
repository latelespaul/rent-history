package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.common.error.ApiException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limit on {@code POST /auth/google} and {@code POST /auth/dev-login} (§13): 20 requests per 15 minutes
 * per client IP, answered with {@code 429} and {@code Retry-After}. In memory per instance (Bucket4j); a
 * shared store comes only when running many instances (§18). In {@code prod} the client IP comes from
 * {@code X-Forwarded-For} only when the direct peer is a trusted proxy ({@code TRUSTED_PROXIES}, Tomcat
 * {@code RemoteIpValve}); any other caller's header is ignored.
 */
@Component
public class SignInRateLimiter {
	
	/** Above this many tracked IPs, idle (full) buckets are dropped so memory stays bounded. */
	static final int MAX_TRACKED_CLIENTS = 10_000;
	
	/** The sweep of idle buckets runs at most this often, so a full map never costs a scan per request. */
	static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);
	
	private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
	private final AtomicLong nextSweep = new AtomicLong(System.nanoTime());
	private final int requests;
	private final Duration period;
	
	public SignInRateLimiter(AuthProperties properties) {
		this.requests = properties.rateLimit().requests();
		this.period = properties.rateLimit().period();
	}
	
	/** @throws ApiException {@code 429 rate_limited} when the caller's IP used up its allowance */
	public void check(HttpServletRequest request) {
		if (buckets.size() > MAX_TRACKED_CLIENTS) {
			sweepIfDue();
		}
		Bucket bucket = buckets.computeIfAbsent(String.valueOf(request.getRemoteAddr()), ip -> newBucket());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			throw ApiException.rateLimited(Duration.ofNanos(probe.getNanosToWaitForRefill()));
		}
	}
	
	private void sweepIfDue() {
		long now = System.nanoTime();
		long due = nextSweep.get();
		if (now - due >= 0 && nextSweep.compareAndSet(due, now + SWEEP_INTERVAL.toNanos())) {
			buckets.values().removeIf(b -> b.getAvailableTokens() >= requests);
		}
	}
	
	private Bucket newBucket() {
		return Bucket.builder().addLimit(limit -> limit.capacity(requests).refillGreedy(requests, period)).build();
	}
	
}
