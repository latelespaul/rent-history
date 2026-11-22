package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.config.BrandProperties;
import com.rentrix.rentrixserver.notification.sms.AdminRelaySmsSender;
import com.rentrix.rentrixserver.notification.sms.SmsSendException;
import com.rentrix.rentrixserver.notification.sms.SmsSender;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Phone verification by SMS code (D-101). Self-service, no admin: a confirmed code marks {@code PHONE_OTP}
 * {@code VERIFIED} and sets {@code users.phone}. The code is stored only as an HMAC, expires after
 * {@code OTP_TTL_MINUTES} (10) and allows 5 attempts. A user can request 3 codes per hour and 10 per day; one number
 * gets at most 5 codes per day across all accounts. Neither the code nor the full number is ever logged or
 * returned (the owner sees the masked number only). This verifies a phone; it is not a sign-in method (D-70).
 * <p>
 * Sends are counted and recorded under a lock on the user row, so one user's parallel requests queue up, and under
 * an in-process lock keyed by the number. The cross-account per-number cap is therefore per instance until a shared
 * store exists. The SMS goes out after that commit, outside every lock; a failed send uses the challenge up, so it
 * still counts toward the limits but can never be confirmed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneOtpService {
	
	static final int MAX_ATTEMPTS = 5;
	static final int CODES_PER_USER_PER_HOUR = 3;
	static final int CODES_PER_USER_PER_DAY = 10;
	static final int CODES_PER_PHONE_PER_DAY = 5;
	
	/** Indian mobile number, E.164: {@code +91} and 10 digits starting 6–9 (D-101). */
	static final Pattern INDIAN_MOBILE = Pattern.compile("^\\+91[6-9][0-9]{9}$");
	
	private static final Pattern SEPARATORS = Pattern.compile("[\\s().-]");
	private static final Pattern CODE = Pattern.compile("^[0-9]{6}$");
	private static final Duration HOUR = Duration.ofHours(1);
	private static final Duration DAY = Duration.ofDays(1);
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Duration LOCK_WAIT = Duration.ofSeconds(3);
	private static final Duration LOCK_RETRY = Duration.ofSeconds(2);
	private static final ReentrantLock[] PHONE_LOCKS = new ReentrantLock[64];
	
	static {
		for (int i = 0; i < PHONE_LOCKS.length; i++) {
			PHONE_LOCKS[i] = new ReentrantLock();
		}
	}
	
	private final PhoneOtpChallengeRepository challenges;
	private final IdentityVerificationRepository checks;
	private final UserRepository users;
	private final IdentityService identity;
	private final PhoneOtpAvailability availability;
	private final BrandProperties brand;
	private final IdHasher hasher;
	private final IdentityProperties properties;
	private final Clock clock;
	private final PlatformTransactionManager transactionManager;
	
	/**
	 * The answer to {@code POST /me/identity/phone/start}. {@code delivery} is {@code SMS} when a provider sends the
	 * code, or {@code TEAM} under the preview-only admin relay (D-137), where the team passes the code on by hand.
	 */
	public record Started(UUID challengeId, Instant expiresAt, String delivery) {
		
		public static final String SMS = "SMS";
		public static final String TEAM = "TEAM";
		
	}
	
	/**
	 * Accepts {@code +91XXXXXXXXXX}, {@code 91XXXXXXXXXX} or a bare 10-digit mobile number, with spaces, dots,
	 * dashes or brackets, and returns it as E.164; empty when it is not an Indian mobile number.
	 */
	public static Optional<String> normalisePhone(String input) {
		if (input == null) {
			return Optional.empty();
		}
		String digits = SEPARATORS.matcher(input.trim()).replaceAll("");
		if (digits.matches("[0-9]{10}")) {
			digits = "+91" + digits;
		} else if (digits.matches("91[0-9]{10}")) {
			digits = "+" + digits;
		}
		return INDIAN_MOBILE.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
	}
	
	public Started start(UUID userId, String phoneInput) {
		SmsSender sender = availability.sender().orElseThrow(PhoneOtpService::unavailable);
		String phone = normalisePhone(phoneInput).orElseThrow(() -> ApiException.validation(List.of(
			new ApiProblem.FieldError("phone", "pattern", "Use an Indian mobile number, e.g. +919876543210."))));
		String phoneHmac = hasher.hmac(phone);
		String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
		PhoneOtpChallenge challenge = reserve(userId, phone, phoneHmac, code);
		try {
			sender.send(phone, code + " is your " + brand.name() + " verification code.");
		} catch (SmsSendException e) {
			challenges.consume(challenge.getId(), MAX_ATTEMPTS, now());
			log.warn("Phone OTP not sent for user {}: {}", userId, e.getMessage());
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "sms_send_failed",
				"The code could not be sent. Try again in a few minutes.");
		}
		log.info("Phone OTP challenge {} started for user {}", challenge.getPublicId(), userId);
		return new Started(challenge.getPublicId(), challenge.getExpiresAt(),
			sender instanceof AdminRelaySmsSender ? Started.TEAM : Started.SMS);
	}
	
	/** Checks the send limits and commits the challenge, under the number's stripe lock and the user row lock. */
	private PhoneOtpChallenge reserve(UUID userId, String phone, String phoneHmac, String code) {
		ReentrantLock phoneLock = PHONE_LOCKS[Math.floorMod(phoneHmac.hashCode(), PHONE_LOCKS.length)];
		try {
			if (!phoneLock.tryLock(LOCK_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
				throw ApiException.rateLimited(LOCK_RETRY);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw ApiException.rateLimited(LOCK_RETRY);
		}
		try {
			return new TransactionTemplate(transactionManager)
				.execute(status -> insertChallenge(userId, phone, phoneHmac, code));
		} finally {
			phoneLock.unlock();
		}
	}
	
	private PhoneOtpChallenge insertChallenge(UUID userId, String phone, String phoneHmac, String code) {
		User user = users.lockByPublicId(userId)
			.filter(u -> u.getStatus() != UserStatus.DELETED)
			.orElseThrow(() -> ApiException.notFound("User"));
		boolean alreadyVerified = checks.findByUserAndMethod(user, IdentityMethod.PHONE_OTP)
			.filter(row -> row.getStatus() == IdentityStatus.VERIFIED)
			.filter(row -> IdHasher.same(row.getValueHmac(), phoneHmac))
			.isPresent();
		if (alreadyVerified) {
			throw ApiException.conflict("phone_already_verified", "This phone number is already verified.");
		}
		Instant now = now();
		checkLimits(challenges.sentToUserSince(user, now.minus(HOUR)), CODES_PER_USER_PER_HOUR, HOUR, now);
		checkLimits(challenges.sentToUserSince(user, now.minus(DAY)), CODES_PER_USER_PER_DAY, DAY, now);
		checkLimits(challenges.sentToPhoneSince(phoneHmac, now.minus(DAY)), CODES_PER_PHONE_PER_DAY, DAY, now);
		
		PhoneOtpChallenge challenge = new PhoneOtpChallenge();
		challenge.setUser(user);
		challenge.setPhoneE164(phone);
		challenge.setPhoneHmac(phoneHmac);
		challenge.setCodeHmac(codeHmac(challenge.getPublicId(), code));
		challenge.setCreatedAt(now);
		challenge.setExpiresAt(now.plus(properties.otpTtl()));
		return challenges.save(challenge);
	}
	
	/**
	 * Checks the code. A wrong code uses up one of the 5 attempts, and that count is kept even though the request
	 * fails, so the transaction does not roll back on {@link ApiException}.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public IdentityView confirm(UUID userId, UUID challengeId, String code) {
		availability.sender().orElseThrow(PhoneOtpService::unavailable);
		User user = load(userId);
		if (code == null || !CODE.matcher(code.trim()).matches()) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("code", "pattern", "Enter the 6-digit code.")));
		}
		PhoneOtpChallenge challenge = challenges.findByPublicIdAndUser(challengeId, user)
			.orElseThrow(() -> ApiException.notFound("Code request"));
		Instant now = now();
		if (challenge.getConsumedAt() != null || !now.isBefore(challenge.getExpiresAt())) {
			throw expired();
		}
		if (challenge.getAttempts() >= MAX_ATTEMPTS) {
			throw attemptsExceeded();
		}
		if (!IdHasher.same(challenge.getCodeHmac(), codeHmac(challenge.getPublicId(), code.trim()))) {
			if (challenges.countWrongAttempt(challenge.getId(), MAX_ATTEMPTS, now) == 0) {
				throw attemptsExceeded();
			}
			int left = MAX_ATTEMPTS - challenge.getAttempts() - 1;
			String message = left == 1 ? "Wrong code. 1 try left." : "Wrong code. " + left + " tries left.";
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "otp_invalid", message,
				List.of(new ApiProblem.FieldError("code", "otp_invalid", message)));
		}
		if (challenges.consume(challenge.getId(), MAX_ATTEMPTS, now) == 0) {
			throw expired();
		}
		markVerified(user, challenge, now);
		return identity.view(user);
	}
	
	private void markVerified(User user, PhoneOtpChallenge challenge, Instant now) {
		String phone = challenge.getPhoneE164();
		IdentityVerification row = checks.findByUserAndMethod(user, IdentityMethod.PHONE_OTP).orElseGet(() -> {
			IdentityVerification created = new IdentityVerification();
			created.setUser(user);
			created.setMethod(IdentityMethod.PHONE_OTP);
			return created;
		});
		row.resetDecision();
		row.setStatus(IdentityStatus.VERIFIED);
		row.setLast4(phone.substring(phone.length() - 4));
		row.setValueHmac(challenge.getPhoneHmac());
		row.setCheckedBy(CheckedBy.OTP);
		row.setVerifiedAt(now);
		checks.save(row);
		identity.revokeOtherPhone(user, IdentityMethod.PHONE_OTP, row.getValueHmac());
		user.setPhone(phone);
		users.save(user);
		long others = identity.phoneVerifiedOnOtherAccounts(user, row.getValueHmac());
		if (others > 0) {
			log.info("Phone verified for user {}; the same phone is verified on {} other account(s): {}",
				user.getPublicId(), others, IdentityService.DUPLICATE_PHONE);
		} else {
			log.info("Phone verified for user {}", user.getPublicId());
		}
	}
	
	/** HMAC of the code, bound to its challenge so a code is only ever valid for the request it was sent for. */
	private String codeHmac(UUID challengeId, String code) {
		return hasher.hmac("otp:" + challengeId + ":" + code);
	}
	
	/** {@code 429} with {@code Retry-After} until the oldest send in the window leaves it. */
	private static void checkLimits(List<Instant> sentOldestFirst, int max, Duration window, Instant now) {
		if (sentOldestFirst.size() >= max) {
			Instant frees = sentOldestFirst.get(sentOldestFirst.size() - max).plus(window);
			throw ApiException.rateLimited(Duration.between(now, frees));
		}
	}
	
	private User load(UUID publicId) {
		return users.findByPublicId(publicId)
			.filter(u -> u.getStatus() != UserStatus.DELETED)
			.orElseThrow(() -> ApiException.notFound("User"));
	}
	
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}
	
	private static ApiException expired() {
		return ApiException.rule("otp_expired", "This code has expired. Request a new one.");
	}
	
	private static ApiException attemptsExceeded() {
		return ApiException.rule("otp_attempts_exceeded", "Too many wrong codes. Request a new one.");
	}
	
	private static ApiException unavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "phone_otp_unavailable",
			"Phone verification is not available right now. Use PAN or Aadhaar instead.");
	}
	
}
