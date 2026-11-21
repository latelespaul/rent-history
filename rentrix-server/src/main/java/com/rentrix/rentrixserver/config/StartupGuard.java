package com.rentrix.rentrixserver.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
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
 * Registered in {@code META-INF/spring.factories}, so it also guards tests.
 */
public class StartupGuard implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
	
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
		if (!StringUtils.hasText(env.getProperty("spring.datasource.url"))) {
			throw new IllegalStateException("Refusing to start: DB_URL is required in the '" + profile
				+ "' profile (a jdbc:sqlite:, jdbc:mysql: or jdbc:postgresql: URL).");
		}
	}
	
	@Override
	public int getOrder() {
		// after ConfigDataEnvironmentPostProcessor has resolved the active profiles
		return Ordered.LOWEST_PRECEDENCE;
	}
	
}
