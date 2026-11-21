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
import java.util.Set;

/**
 * Fail closed at startup (D-64, D-77). Runs once the profiles and config files are loaded
 * and before any bean exists, so a bad deployment stops with one clear message:
 * <ul>
 *   <li>exactly one of {@code dev}, {@code test}, {@code prod} must be active; there is no default;</li>
 *   <li>{@code JWT_SECRET} must be at least 32 bytes (dev and test ship a built-in one);</li>
 *   <li>outside dev and test, {@code DEV_LOGIN_ENABLED=true} is refused and {@code DB_URL} is required.</li>
 * </ul>
 * <p>An empty {@code CORS_ALLOWED_ORIGINS} outside dev and test is allowed with one warning: the
 * allow-list stays empty, so no cross-origin browser call is accepted (still fail closed). This keeps
 * an existing deployment that predates the variable (Render) starting.
 * <p>A {@code jdbc:sqlite:} {@code DB_URL} outside dev and test also starts with one warning: the
 * data is lost on redeploy unless {@code /app/data} is on a persistent disk.
 * <p>Registered in {@code META-INF/spring.factories}, so it also guards tests.
 */
public class StartupGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
	
	private static final Logger LOG = LoggerFactory.getLogger(StartupGuard.class);
	
	static final Set<String> RUNTIME_PROFILES = Set.of("dev", "test", "prod");
	static final Set<String> LOCAL_PROFILES = Set.of("dev", "test");
	static final int MIN_JWT_SECRET_BYTES = 32;
	
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
		if (LOCAL_PROFILES.contains(profile)) {
			return;
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
