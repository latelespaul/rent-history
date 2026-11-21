package com.rentrix.rentrixserver.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an id: the caller's {@code X-Request-Id} when it looks safe, else a new
 * one. The id is put in the log MDC as {@code traceId} (so every JSON log line carries it),
 * echoed in the response header, and returned as {@code traceId} in error bodies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
	
	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "traceId";
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
	
	/** The current request's id, or null outside a request. */
	public static String currentRequestId() {
		return MDC.get(MDC_KEY);
	}
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String incoming = request.getHeader(HEADER);
		String id = incoming != null && SAFE_ID.matcher(incoming).matches()
			? incoming
			: UUID.randomUUID().toString().replace("-", "");
		MDC.put(MDC_KEY, id);
		response.setHeader(HEADER, id);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
	
}
