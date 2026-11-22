package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.notification.sms.PhoneMasks;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import com.rentrix.rentrixserver.verification.VerificationApplicationRepository;
import com.rentrix.rentrixserver.verification.VerificationEvent;
import com.rentrix.rentrixserver.verification.VerificationEventRepository;
import com.rentrix.rentrixserver.verification.VerificationEventType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Phone verification with Truecaller on a mobile website (Android only), as Truecaller's web SDK describes it:
 * <ol>
 *   <li>{@code POST /me/identity/truecaller/start} makes a request nonce for the signed-in user and returns the
 *   {@code truecallersdk://} deep link that opens the Truecaller consent sheet;</li>
 *   <li>Truecaller posts to {@code POST /auth/truecaller/callback}: {@code {requestId, status: "flow_invoked"}} when
 *   the sheet opened, {@code {requestId, status: "user_rejected"}} when the user declined, and
 *   {@code {requestId, accessToken, endpoint}} when they agreed. The answer must come within 3 s, so the profile
 *   fetch runs in the background;</li>
 *   <li>the server fetches the profile from {@code endpoint} (HTTPS Truecaller profile hosts only) with the access
 *   token and marks method {@code TRUECALLER} {@code VERIFIED} ({@code checked_by=PROVIDER}); it counts as the
 *   verified phone exactly like a phone confirmed by SMS code ({@link IdentityService#PHONE_METHODS}), and sets
 *   {@code users.phone};</li>
 *   <li>the web polls {@code GET /me/identity/truecaller/{requestId}} until the status is final.</li>
 * </ol>
 * A nonce is bound to one user, lives {@code requestTtl} (10 minutes) and takes one access token. Requests are
 * kept in memory (one instance; a restart only drops requests in flight, and the user starts again). Only the
 * masked phone and its HMAC are stored: no token, no profile. The app key and the access token are never logged.
 */
@Slf4j
@Service
public class TruecallerService {

	/** Truecaller's profile hosts, for example {@code profile4.truecaller.com} or {@code profile4-noneu.truecaller.com}. */
	static final Pattern PROFILE_HOST = Pattern.compile("^profile[0-9]*(-[a-z0-9]+)*\\.truecaller\\.com$");

	private static final Pattern STATUS_WORD = Pattern.compile("^[a-z_]{1,32}$");
	
	/** Truecaller wants a nonce of 8 to 64 characters. */
	private static final Pattern NONCE = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

	/** How long the consent sheet stays open before Truecaller dismisses it (the web stops polling after this). */
	static final Duration SHEET_TTL = Duration.ofMinutes(2);

	private static final SecureRandom RANDOM = new SecureRandom();

	/** Where one request stands. {@code VERIFIED}, {@code REJECTED}, {@code FAILED} and {@code EXPIRED} are final. */
	public enum Status {
		/** Waiting for Truecaller. */
		PENDING,
		/** Truecaller opened the consent sheet ({@code flow_invoked}). */
		INVOKED,
		/** The user agreed; the profile is being fetched. */
		VERIFYING,
		VERIFIED,
		/** The user declined or chose another method. */
		REJECTED,
		/** The profile could not be fetched or its number is not an Indian mobile. */
		FAILED,
		/** No answer within the request's lifetime. */
		EXPIRED;

		boolean open() {
			return this == PENDING || this == INVOKED;
		}
	}

	/** The answer to {@code start}: open {@code deepLink} on the phone, then poll {@code requestId}. */
	public record Started(String requestId, String deepLink, Instant expiresAt) {
	}

	/** The answer to the status poll; {@code masked} is the verified number, {@code reason} why it failed. */
	public record RequestView(String requestId, Status status, String masked, String reason) {
	}

	private static final class Request {

		final String id;
		final UUID userId;
		final Instant expiresAt;
		Status status = Status.PENDING;
		String masked;
		String reason;

		Request(String id, UUID userId, Instant expiresAt) {
			this.id = id;
			this.userId = userId;
			this.expiresAt = expiresAt;
		}

		synchronized RequestView view(Instant now) {
			Status shown = status.open() && !now.isBefore(expiresAt) ? Status.EXPIRED : status;
			return new RequestView(id, shown, masked, reason);
		}

	}

	private final Map<String, Request> requests = new ConcurrentHashMap<>();
	private final ExecutorService fetches = Executors.newVirtualThreadPerTaskExecutor();

	private final TruecallerProperties properties;
	private final PhoneVerifier verifier;
	private final IdHasher hasher;
	private final IdentityService identity;
	private final IdentityVerificationRepository checks;
	private final UserRepository users;
	private final VerificationApplicationRepository applications;
	private final VerificationEventRepository events;
	private final Clock clock;
	private final TransactionTemplate transactions;

	public TruecallerService(TruecallerProperties properties, PhoneVerifier verifier, IdHasher hasher,
		IdentityService identity, IdentityVerificationRepository checks, UserRepository users, VerificationApplicationRepository applications,
		VerificationEventRepository events, Clock clock, PlatformTransactionManager transactionManager) {
		this.properties = properties;
		this.verifier = verifier;
		this.hasher = hasher;
		this.identity = identity;
		this.checks = checks;
		this.users = users;
		this.applications = applications;
		this.events = events;
		this.clock = clock;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@PreDestroy
	void stop() {
		fetches.shutdownNow();
	}

	/** Whether the "Verify with Truecaller" button can be offered: switched on, keyed and able to hash the phone. */
	public boolean available() {
		return identity.truecallerAvailable();
	}

	/** A new request for {@code userId}; an older open request of the same user is dropped. */
	public Started start(UUID userId) {
		if (!available()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "truecaller_unavailable",
				"Truecaller verification is not available. Verify your phone with an SMS code instead.");
		}
		users.findByPublicId(userId).filter(u -> u.getStatus() != UserStatus.DELETED)
			.orElseThrow(() -> ApiException.notFound("User"));
		Instant now = now();
		requests.values().removeIf(r -> r.userId.equals(userId) || !now.isBefore(r.expiresAt));
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Request request = new Request(nonce, userId, now.plus(properties.requestTtl()));
		requests.put(nonce, request);
		log.info("Truecaller request started for user {}", userId);
		return new Started(nonce, deepLink(nonce), request.expiresAt);
	}

	/** The status poll; a request of another user is {@code 404}. */
	public RequestView status(UUID userId, String requestId) {
		Request request = requestId == null ? null : requests.get(requestId);
		if (request == null || !request.userId.equals(userId)) {
			throw ApiException.notFound("Truecaller request");
		}
		return request.view(now());
	}

	/** A Truecaller callback, already parsed. Unknown, expired or used nonces are ignored. */
	public void callback(String requestId, String status, String accessToken, String endpoint) {
		Request request = requestId == null || !NONCE.matcher(requestId).matches() ? null : requests.get(requestId);
		Instant now = now();
		if (request == null || !now.isBefore(request.expiresAt)) {
			log.info("Truecaller callback for an unknown or expired request ignored");
			return;
		}
		if (accessToken == null || accessToken.isBlank()) {
			// Truecaller's status words are lower-case snake case; anything else is logged and kept as "declined".
			String given = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
			String code = STATUS_WORD.matcher(given).matches() ? given : "declined";
			synchronized (request) {
				if (!request.status.open()) {
					return;
				}
				if (code.equals("flow_invoked")) {
					request.status = Status.INVOKED;
				} else {
					// user_rejected, or the sheet's "use another method" footer
					request.status = Status.REJECTED;
					request.reason = code;
				}
			}
			log.info("Truecaller request of user {}: {}", request.userId, code);
			return;
		}
		synchronized (request) {
			if (!request.status.open()) {
				log.info("Truecaller access token for a used request of user {} ignored", request.userId);
				return;
			}
			request.status = Status.VERIFYING;
		}
		Optional<URI> profile = profileEndpoint(endpoint);
		if (profile.isEmpty()) {
			log.warn("Truecaller callback for user {} named a profile endpoint outside Truecaller; refused",
				request.userId);
			finish(request, Status.FAILED, null, "endpoint_refused");
			return;
		}
		fetches.execute(() -> complete(request, profile.get(), accessToken));
	}

	private void complete(Request request, URI endpoint, String accessToken) {
		String phone;
		try {
			phone = PhoneOtpService.normalisePhone(verifier.fetchPhone(endpoint, accessToken)).orElse(null);
		} catch (RuntimeException e) {
			log.warn("Truecaller profile fetch failed for user {}: {}", request.userId,
				e instanceof PhoneVerifier.VerifierException ? e.getMessage() : e.getClass().getSimpleName());
			finish(request, Status.FAILED, null, "profile_unavailable");
			return;
		}
		if (phone == null) {
			log.info("Truecaller number of user {} is not an Indian mobile number", request.userId);
			finish(request, Status.FAILED, null, "not_indian_mobile");
			return;
		}
		try {
			transactions.executeWithoutResult(tx -> markVerified(request.userId, phone));
		} catch (RuntimeException e) {
			log.warn("Truecaller verification of user {} could not be saved: {}", request.userId,
				e.getClass().getSimpleName());
			finish(request, Status.FAILED, null, "not_saved");
			return;
		}
		finish(request, Status.VERIFIED, PhoneMasks.mask(phone), null);
	}

	private static void finish(Request request, Status status, String masked, String reason) {
		synchronized (request) {
			request.status = status;
			request.masked = masked;
			request.reason = reason;
		}
	}

	/**
	 * Method {@code TRUECALLER}, the same shape as a confirmed SMS code (D-101): last 4 digits, the phone HMAC and
	 * {@code verified_at}. A verified SMS phone with another number is revoked, since the account has one phone.
	 */
	private void markVerified(UUID userId, String phone) {
		User user = users.lockByPublicId(userId)
			.filter(u -> u.getStatus() != UserStatus.DELETED)
			.orElseThrow(() -> ApiException.notFound("User"));
		Instant now = now();
		IdentityVerification row = checks.findByUserAndMethod(user, IdentityMethod.TRUECALLER).orElseGet(() -> {
			IdentityVerification created = new IdentityVerification();
			created.setUser(user);
			created.setMethod(IdentityMethod.TRUECALLER);
			return created;
		});
		row.resetDecision();
		row.setStatus(IdentityStatus.VERIFIED);
		row.setLast4(phone.substring(phone.length() - 4));
		row.setValueHmac(hasher.hmac(phone));
		row.setCheckedBy(CheckedBy.PROVIDER);
		row.setProvider("TRUECALLER");
		row.setProviderRef(null);
		row.setVerifiedAt(now);
		checks.save(row);
		identity.revokeOtherPhone(user, IdentityMethod.TRUECALLER, row.getValueHmac());
		user.setPhone(phone);
		users.save(user);
		applications.findByUser(user).forEach(application -> {
			VerificationEvent event = new VerificationEvent();
			event.setApplication(application);
			event.setRevision(application.getRevision());
			event.setEvent(VerificationEventType.PHONE_VERIFIED);
			event.setActor(user);
			event.setCode("TRUECALLER");
			event.setCreatedAt(now);
			events.save(event);
		});
		long others = identity.phoneVerifiedOnOtherAccounts(user, row.getValueHmac());
		log.info("Phone verified with Truecaller for user {}{}", userId,
			others > 0 ? "; the same phone is verified on " + others + " other account(s): "
				+ IdentityService.DUPLICATE_PHONE : "");
	}

	/** The callback's {@code endpoint} if it is {@code https://<Truecaller profile host>/...} on the default port. */
	static Optional<URI> profileEndpoint(String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = new URI(endpoint.trim());
			String host = uri.getHost();
			boolean ok = "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
				&& (uri.getPort() == -1 || uri.getPort() == 443) && host != null
				&& PROFILE_HOST.matcher(host.toLowerCase(Locale.ROOT)).matches();
			return ok ? Optional.of(uri) : Optional.empty();
		} catch (URISyntaxException e) {
			return Optional.empty();
		}
	}

	private String deepLink(String nonce) {
		return "truecallersdk://truesdk/web_verify?type=btmsheet"
			+ "&requestNonce=" + nonce
			+ "&partnerKey=" + encode(properties.appKey())
			+ "&partnerName=" + encode(properties.appName())
			+ "&lang=en"
			+ "&loginPrefix=continue"
			+ "&loginSuffix=verifymobile"
			+ "&ctaPrefix=continuewith"
			+ "&btnShape=round"
			+ "&skipOption=useanothermethod"
			+ "&ttl=" + SHEET_TTL.toMillis();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}

}
