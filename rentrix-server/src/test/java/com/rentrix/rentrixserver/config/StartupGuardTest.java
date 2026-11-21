package com.rentrix.rentrixserver.config;

import com.rentrix.rentrixserver.RentrixServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;

class StartupGuardTest {
	
	private static final String GOOD_SECRET = "a-production-secret-that-is-long-enough-0123456789";
	
	private static MockEnvironment env(String... profiles) {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles(profiles);
		return env;
	}
	
	private static MockEnvironment prod() {
		return env("prod")
			.withProperty("app.jwt.secret", GOOD_SECRET)
			.withProperty("spring.datasource.url", "jdbc:postgresql://db/rentrix");
	}
	
	@Test
	void refusesWithoutProfile() {
		assertThatThrownBy(() -> StartupGuard.check(env()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("SPRING_PROFILES_ACTIVE");
	}
	
	@Test
	void refusesUnknownOrConflictingProfiles() {
		assertThatThrownBy(() -> StartupGuard.check(env("docker"))).hasMessageContaining("exactly one");
		assertThatThrownBy(() -> StartupGuard.check(env("dev", "prod"))).hasMessageContaining("exactly one");
	}
	
	@Test
	void devAndTestStartWithTheirBuiltInSecret() {
		assertThatCode(() -> StartupGuard.check(env("dev").withProperty("app.jwt.secret", GOOD_SECRET)
			.withProperty("app.dev-login.enabled", "true"))).doesNotThrowAnyException();
		assertThatCode(() -> StartupGuard.check(env("test").withProperty("app.jwt.secret", GOOD_SECRET)))
			.doesNotThrowAnyException();
	}
	
	@Test
	void refusesShortOrMissingJwtSecret() {
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.jwt.secret", "")))
			.hasMessageContaining("JWT_SECRET");
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.jwt.secret", "x".repeat(31))))
			.hasMessageContaining("JWT_SECRET");
		assertThatThrownBy(() -> StartupGuard.check(env("dev").withProperty("app.jwt.secret", "short")))
			.hasMessageContaining("JWT_SECRET");
	}
	
	@Test
	void prodRefusesDevLoginAndMissingDatabaseUrl() {
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.dev-login.enabled", "true")))
			.hasMessageContaining("DEV_LOGIN_ENABLED");
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("spring.datasource.url", "")))
			.hasMessageContaining("DB_URL");
	}
	
	@Test
	void prodStartsWhenConfigured() {
		assertThatCode(() -> StartupGuard.check(prod())).doesNotThrowAnyException();
	}
	
	@Test
	void applicationRefusesToStartWithoutProfile() {
		assumeTrue(System.getenv("SPRING_PROFILES_ACTIVE") == null, "SPRING_PROFILES_ACTIVE is set in this shell");
		SpringApplication app = new SpringApplication(RentrixServerApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.setDefaultProperties(Map.of("spring.profiles.active", ""));
		assertThatThrownBy(app::run)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("SPRING_PROFILES_ACTIVE");
	}
	
	@Test
	void applicationRefusesProdWithoutSecret() {
		assumeTrue(System.getenv("JWT_SECRET") == null, "JWT_SECRET is set in this shell");
		SpringApplication app = new SpringApplication(RentrixServerApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.setAdditionalProfiles("prod");
		Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(app::run);
		assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
	}
	
}
