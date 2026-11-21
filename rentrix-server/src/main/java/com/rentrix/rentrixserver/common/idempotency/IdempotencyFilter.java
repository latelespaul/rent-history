package com.rentrix.rentrixserver.common.idempotency;

import com.rentrix.rentrixserver.common.error.ProblemWriter;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code Idempotency-Key} support for endpoints marked {@link Idempotent} (§12.1).
 * <ol>
 *   <li>No header, not a write, or not an {@code @Idempotent} endpoint: the request runs as usual.</li>
 *   <li>First request with a key: the key is reserved (unique per caller), the request runs, and a
 *       2xx response (status, Content-Type, Location, body) is stored for 24 h. A non-2xx response releases the key so the client can fix
 *       and retry with it.</li>
 *   <li>Same key and same request again: the stored response is replayed with
 *       {@code Idempotent-Replayed: true}; the write does not run twice.</li>
 *   <li>Same key, different request: {@code 422 idempotency_key_reused}.</li>
 *   <li>Same key while the first request still runs: {@code 409 idempotency_request_in_progress}. A
 *       reservation older than {@link IdempotencyStore#IN_PROGRESS_LEASE} is taken to be abandoned (its
 *       instance died) and the retry runs.</li>
 * </ol>
 * Keys are scoped to the authenticated caller, so it runs after the security filter chain.
 */
@Slf4j
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 10)
public class IdempotencyFilter extends OncePerRequestFilter {
	
	public static final String HEADER = "Idempotency-Key";
	public static final String REPLAYED_HEADER = "Idempotent-Replayed";
	/** Responses larger than this are not stored; the key is released instead. */
	static final int MAX_STORED_BODY_BYTES = 64 * 1024;
	
	private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
	private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH");
	
	private final IdempotencyStore store;
	private final ProblemWriter problems;
	private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;
	
	public IdempotencyFilter(IdempotencyStore store, ProblemWriter problems,
		@Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
		this.store = store;
		this.problems = problems;
		this.handlerMapping = handlerMapping;
	}
	
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !WRITE_METHODS.contains(request.getMethod()) || request.getHeader(HEADER) == null;
	}
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		Optional<String> scope = callerScope();
		if (scope.isEmpty() || !isIdempotentEndpoint(request)) {
			chain.doFilter(request, response);
			return;
		}
		String key = request.getHeader(HEADER);
		if (!VALID_KEY.matcher(key).matches()) {
			problems.write(request, response, HttpStatus.BAD_REQUEST, "idempotency_key_invalid",
				"Idempotency-Key must be 1-64 characters from A-Z a-z 0-9 . _ : -.");
			return;
		}
		CachedBodyRequest cached = new CachedBodyRequest(request);
		String hash = requestHash(cached);
		
		Optional<IdempotencyRecord> existing = store.findLive(scope.get(), key);
		if (existing.isPresent()) {
			answerRepeat(existing.get(), hash, request, response);
			return;
		}
		IdempotencyRecord record;
		try {
			record = store.reserve(scope.get(), key, hash);
		} catch (DataIntegrityViolationException raced) {
			inProgress(request, response);
			return;
		}
		
		ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
		boolean keepReservation = false;
		try {
			chain.doFilter(cached, captured);
			int status = captured.getStatus();
			byte[] body = captured.getContentAsByteArray();
			if (status >= 200 && status < 300 && body.length <= MAX_STORED_BODY_BYTES) {
				keepReservation = true;
				store.complete(record.getId(), status, captured.getContentType(),
					captured.getHeader(HttpHeaders.LOCATION), new String(body, StandardCharsets.UTF_8));
			} else if (status >= 200 && status < 300) {
				log.warn("Idempotent response of {} bytes on {} is too large to store; key released", body.length,
					request.getRequestURI());
			}
		} catch (RuntimeException bookkeeping) {
			if (!keepReservation) {
				throw bookkeeping;
			}
			log.warn("Could not store idempotent response on {}; key stays reserved until its lease ends",
				request.getRequestURI(), bookkeeping);
		} finally {
			try {
				if (!keepReservation) {
					store.release(record.getId());
				}
			} catch (RuntimeException releaseFailed) {
				log.warn("Could not release Idempotency-Key reservation {} on {}", record.getId(),
					request.getRequestURI(), releaseFailed);
			} finally {
				captured.copyBodyToResponse();
			}
		}
	}
	
	private void answerRepeat(IdempotencyRecord record, String hash, HttpServletRequest request,
		HttpServletResponse response) throws IOException {
		if (!record.getRequestHash().equals(hash)) {
			problems.write(request, response, HttpStatus.UNPROCESSABLE_CONTENT, "idempotency_key_reused",
				"This Idempotency-Key was already used for a different request.");
		} else if (record.getState() == IdempotencyState.COMPLETED) {
			response.setStatus(record.getResponseStatus());
			if (record.getResponseContentType() != null) {
				response.setContentType(record.getResponseContentType());
			}
			if (record.getResponseLocation() != null) {
				response.setHeader(HttpHeaders.LOCATION, record.getResponseLocation());
			}
			response.setHeader(REPLAYED_HEADER, "true");
			byte[] body = record.getResponseBody() == null ? new byte[0]
				: record.getResponseBody().getBytes(StandardCharsets.UTF_8);
			response.setContentLength(body.length);
			response.getOutputStream().write(body);
		} else {
			inProgress(request, response);
		}
	}
	
	private void inProgress(HttpServletRequest request, HttpServletResponse response) throws IOException {
		problems.write(request, response, HttpStatus.CONFLICT, "idempotency_request_in_progress",
			"A request with this Idempotency-Key is still running. Retry later.");
	}
	
	private static Optional<String> callerScope() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
			return Optional.of(user.publicId().toString());
		}
		return Optional.empty();
	}
	
	private boolean isIdempotentEndpoint(HttpServletRequest request) {
		RequestMappingHandlerMapping mapping = handlerMapping.getIfAvailable();
		if (mapping == null) {
			return false;
		}
		try {
			HandlerExecutionChain handler = mapping.getHandler(request);
			return handler != null && handler.getHandler() instanceof HandlerMethod method
				&& method.hasMethodAnnotation(Idempotent.class);
		} catch (Exception notMapped) {
			return false; // no handler, wrong method, ...: let the dispatcher answer as usual
		}
	}
	
	/** SHA-256 over method, path, query and body. */
	static String requestHash(CachedBodyRequest request) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
			digest.update((byte) ' ');
			digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
			if (request.getQueryString() != null) {
				digest.update((byte) '?');
				digest.update(request.getQueryString().getBytes(StandardCharsets.UTF_8));
			}
			digest.update((byte) '\n');
			digest.update(request.body);
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}
	
	/** Reads the body once so it can be hashed and still be read by the controller. */
	static final class CachedBodyRequest extends HttpServletRequestWrapper {
		
		private final byte[] body;
		
		CachedBodyRequest(HttpServletRequest request) throws IOException {
			super(request);
			this.body = request.getInputStream().readAllBytes();
		}
		
		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream in = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public int read() {
					return in.read();
				}
				
				@Override
				public int read(byte[] b, int off, int len) {
					return in.read(b, off, len);
				}
				
				@Override
				public boolean isFinished() {
					return in.available() == 0;
				}
				
				@Override
				public boolean isReady() {
					return true;
				}
				
				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException("Async reads are not supported");
				}
			};
		}
		
		@Override
		public BufferedReader getReader() {
			String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8";
			return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.Charset.forName(encoding)));
		}
		
	}
	
}
