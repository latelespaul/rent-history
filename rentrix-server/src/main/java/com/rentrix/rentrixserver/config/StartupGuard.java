package com.rentrix.rentrixserver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fail closed at startup (D-64, D-77). Runs once the profiles and config files are loaded
 * and before any bean exists, so a bad deployment stops with one clear message:
 * <ul>
 *   <li>exactly one of {@code dev}, {@code test}, {@code prod} must be active; there is no default;</li>
 *   <li>{@code JWT_SECRET} must be at least 32 bytes (dev and test ship a built-in one);</li>
 *   <li>outside dev and test, {@code DEV_LOGIN_ENABLED=true} is refused and {@code DB_URL} is required;</li>
 *   <li>{@code SMS_BACKEND} is empty, {@code log}, {@code http} or {@code admin-relay}; {@code log} (codes in the
 *   log) is refused outside dev and test; {@code http} needs {@code SMS_HTTP_URL}, {@code SMS_API_KEY} and an
 *   {@code ID_HASH_KEY}; {@code admin-relay} (codes shown to admins, D-137) is refused outside dev and test unless
 *   {@code PREVIEW_MODE=true}, needs an {@code ID_HASH_KEY} there, and logs one warning on every start;</li>
 *   <li>a set {@code ID_HASH_KEY} is at least 32 bytes (dev and test ship a built-in one).</li>
 * </ul>
 * <p>No {@code SMS_BACKEND} outside dev and test starts with one warning: phone OTP is unavailable
 * ({@code 503 phone_otp_unavailable}, D-101), so a deployment without an SMS provider keeps starting.
 * <p>An empty {@code CORS_ALLOWED_ORIGINS} outside dev and test is allowed with one warning: the
 * allow-list stays empty, so no cross-origin browser call is accepted (still fail closed). This keeps
 * an existing deployment that predates the variable (Render) starting.
 * <p>A {@code jdbc:sqlite:} {@code DB_URL} outside dev and test also starts with one warning: the
 * data is lost on redeploy unless {@code /app/data} is on a persistent disk.
 * <p>An empty {@code GOOGLE_CLIENT_ID} outside dev and test starts with one warning too: Google sign-in
 * stays off ({@code 503}), like an empty CORS list, so a deployment without it (Render, the compose smoke
 * test) keeps starting.
 * <p>Registered in {@code META-INF/spring.factories}, so it also guards tests.
 */
public class StartupGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
	
	private static final Logger LOG = LoggerFactory.getLogger(StartupGuard.class);
	
	static final Set<String> RUNTIME_PROFILES = Set.of("dev", "test", "prod");
	static final Set<String> LOCAL_PROFILES = Set.of("dev", "test");
	static final int MIN_JWT_SECRET_BYTES = 32;
	static final int MIN_ID_HASH_KEY_BYTES = 32;
	static final Set<String> SMS_BACKENDS = Set.of("log", "http", "admin-relay");
	
	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		check(event.getEnvironment());
	}
	
	/** Throws {@link IllegalStateException} naming the first problem found. */
	public static void check(ConfigurableEnvironment env) {
		List<String> runtime = Arrays.stream(env.getActiveProfiles()).filter(RUNTIME_PROFILES::contains).toList();
		if (runtime.size() != 1) {
			throw new IllegalStateException("Refusing to start: set SPRING_PROFILES_ACTIVE to exactly one of "
				+ "dev, test or prod (active profiles: " + Arrays.toString(env.getActiveProfiles()) + ").");
		}
		String profile = runtime.getFirst();
		
		String secret = env.getProperty("app.jwt.secret", "");
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
			throw new IllegalStateException("Refusing to start: JWT_SECRET must be at least " + MIN_JWT_SECRET_BYTES
				+ " bytes in the '" + profile + "' profile.");
		}
		String hashKey = env.getProperty("app.identity.hash-key", "");
		boolean hasHashKey = StringUtils.hasText(hashKey);
		if (hasHashKey && hashKey.getBytes(StandardCharsets.UTF_8).length < MIN_ID_HASH_KEY_BYTES) {
			throw new IllegalStateException("Refusing to start: ID_HASH_KEY must be at least " + MIN_ID_HASH_KEY_BYTES
				+ " bytes.");
		}
		String sms = env.getProperty("app.sms.backend", "").trim().toLowerCase(Locale.ROOT);
		if (!sms.isEmpty() && !SMS_BACKENDS.contains(sms)) {
			throw new IllegalStateException("Refusing to start: SMS_BACKEND must be empty, 'log', 'http' or "
				+ "'admin-relay'.");
		}
		if (sms.equals("admin-relay")) {
			LOG.warn("SMS_BACKEND=admin-relay in the '{}' profile: no SMS is sent; phone OTP codes are shown to "
				+ "admins in the admin console. Preview only: set SMS_BACKEND=http before opening sign-up to the "
				+ "public.", profile);
		}
		if (sms.equals("http")) {
			if (!StringUtils.hasText(env.getProperty("app.sms.http.url"))
				|| !StringUtils.hasText(env.getProperty("app.sms.http.api-key"))) {
				throw new IllegalStateException("Refusing to start: SMS_BACKEND=http needs SMS_HTTP_URL and SMS_API_KEY.");
			}
			if (!hasHashKey) {
				throw new IllegalStateException("Refusing to start: SMS_BACKEND=http needs ID_HASH_KEY (at least "
					+ MIN_ID_HASH_KEY_BYTES + " bytes) to hash the OTP codes.");
			}
		}
		if (LOCAL_PROFILES.contains(profile)) {
			return;
		}
		if (sms.equals("log")) {
			throw new IllegalStateException("Refusing to start: SMS_BACKEND=log writes OTP codes to the log and is "
				+ "allowed only in the dev and test profiles.");
		}
		if (sms.equals("admin-relay")) {
			if (!env.getProperty("app.preview-mode", Boolean.class, false)) {
				throw new IllegalStateException("Refusing to start: SMS_BACKEND=admin-relay shows OTP codes to admins and "
					+ "is allowed in the '" + profile + "' profile only with PREVIEW_MODE=true.");
			}
			if (!hasHashKey) {
				throw new IllegalStateException("Refusing to start: SMS_BACKEND=admin-relay needs ID_HASH_KEY (at least "
					+ MIN_ID_HASH_KEY_BYTES + " bytes) to hash the OTP codes.");
			}
		}
		if (sms.isEmpty()) {
			LOG.warn("SMS_BACKEND is empty in the '{}' profile: phone OTP verification is unavailable (503 "
				+ "phone_otp_unavailable). Set SMS_BACKEND=http with SMS_HTTP_URL and SMS_API_KEY to offer it.", profile);
		}
		if (env.getProperty("app.dev-login.enabled", Boolean.class, false)) {
			throw new IllegalStateException("Refusing to start: DEV_LOGIN_ENABLED=true is allowed only in the dev "
				+ "and test profiles.");
		}
		String dbUrl = env.getProperty("spring.datasource.url");
		if (!StringUtils.hasText(dbUrl)) {
			throw new IllegalStateException("Refusing to start: DB_URL is required in the '" + profile
				+ "' profile (a jdbc:sqlite:, jdbc:mysql: or jdbc:postgresql: URL).");
		}
		if (dbUrl.startsWith("jdbc:sqlite:")) {
			LOG.warn("DB_URL is SQLite ({}) in the '{}' profile: the data lives inside the container at that path "
				+ "and is lost on redeploy unless a persistent disk is mounted at /app/data or DB_URL points to a "
				+ "managed database.", dbUrl, profile);
		}
		if (!StringUtils.hasText(env.getProperty("app.auth.google.client-id"))) {
			LOG.warn("GOOGLE_CLIENT_ID is empty in the '{}' profile: Google sign-in is off and POST /auth/google "
				+ "answers 503, so nobody can sign in. Set it to the OAuth web client id (README).", profile);
		}
		boolean hasOrigin = Binder.get(env).bind("app.cors.allowed-origins", Bindable.listOf(String.class))
			.orElse(List.of()).stream().anyMatch(StringUtils::hasText);
		if (!hasOrigin) {
			LOG.warn("CORS_ALLOWED_ORIGINS is empty in the '{}' profile: no cross-origin browser requests are "
				+ "allowed. Set it to the web app origin(s), comma separated.", profile);
		}
	}
	
	@Override
	public int getOrder() {
		// after ConfigDataEnvironmentPostProcessor has resolved the active profiles
		return Ordered.LOWEST_PRECEDENCE;
	}
	
}
