package com.rentrix.rentrixserver.config;

import com.rentrix.rentrixserver.RentrixServerApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;

@ExtendWith(OutputCaptureExtension.class)
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
			.withProperty("spring.datasource.url", "jdbc:postgresql://db/rentrix")
			.withProperty("app.cors.allowed-origins", "https://rentrix.in")
			.withProperty("app.auth.google.client-id", "123-prod.apps.googleusercontent.com");
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
	void prodStartsWithEmptyCorsAllowList() {
		// Render's existing deployment sets no CORS_ALLOWED_ORIGINS: start, warn, allow no origin.
		assertThatCode(() -> StartupGuard.check(prod().withProperty("app.cors.allowed-origins", "")))
			.doesNotThrowAnyException();
		assertThatCode(() -> StartupGuard.check(prod().withProperty("app.cors.allowed-origins", " , ")))
			.doesNotThrowAnyException();
		MockEnvironment unset = env("prod").withProperty("app.jwt.secret", GOOD_SECRET)
			.withProperty("spring.datasource.url", "jdbc:postgresql://db/rentrix");
		assertThatCode(() -> StartupGuard.check(unset)).doesNotThrowAnyException();
	}
	
	@Test
	void prodStillRefusesShortSecretWithoutCorsOrigins() {
		MockEnvironment weak = env("prod").withProperty("app.jwt.secret", "too-short")
			.withProperty("spring.datasource.url", "jdbc:sqlite:/app/data/rentrix.db");
		assertThatThrownBy(() -> StartupGuard.check(weak)).hasMessageContaining("JWT_SECRET");
	}
	
	@Test
	void prodWarnsWhenRunningOnSqlite(CapturedOutput output) {
		assertThatCode(() -> StartupGuard.check(prod()
			.withProperty("spring.datasource.url", "jdbc:sqlite:/app/data/rentrix.db"))).doesNotThrowAnyException();
		assertThat(output).contains("DB_URL is SQLite (jdbc:sqlite:/app/data/rentrix.db)", "/app/data");
	}
	
	@Test
	void prodDoesNotWarnAboutSqliteOnManagedDatabase(CapturedOutput output) {
		StartupGuard.check(prod());
		assertThat(output).doesNotContain("DB_URL is SQLite");
	}
	
	@Test
	void prodStartsWithoutGoogleClientIdButWarns(CapturedOutput output) {
		// Like an empty CORS list: Render and the compose smoke test set no GOOGLE_CLIENT_ID yet.
		assertThatCode(() -> StartupGuard.check(prod().withProperty("app.auth.google.client-id", "")))
			.doesNotThrowAnyException();
		assertThat(output).contains("GOOGLE_CLIENT_ID is empty in the 'prod' profile");
	}
	
	@Test
	void prodDoesNotWarnAboutGoogleWhenConfigured(CapturedOutput output) {
		StartupGuard.check(prod());
		assertThat(output).doesNotContain("GOOGLE_CLIENT_ID is empty");
	}
	
	@Test
	void applicationRefusesProdWithDevLoginEnabled() {
		SpringApplication app = new SpringApplication(RentrixServerApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.setAdditionalProfiles("prod");
		// Command-line arguments outrank the config files, like real env vars do.
		assertThatThrownBy(() -> app.run("--app.jwt.secret=" + GOOD_SECRET,
				"--spring.datasource.url=jdbc:sqlite:target/test-data/never-created.db", "--app.dev-login.enabled=true"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("DEV_LOGIN_ENABLED");
	}
	
	@Test
	void prodRefusesTheFakeTruecallerVerifier() {
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.truecaller.fake", "true")))
			.hasMessageContaining("TRUECALLER_FAKE");
		assertThatCode(() -> StartupGuard.check(prod().withProperty("app.truecaller.fake", "false")))
			.doesNotThrowAnyException();
	}
	
	@Test
	void prodRefusesTheFakeTelegramGatewayAndATokenWithoutAHashKey() {
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.telegram-gateway.fake", "true")))
			.hasMessageContaining("TELEGRAM_GATEWAY_FAKE");
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.telegram-gateway.token", "tg-token")))
			.hasMessageContaining("TELEGRAM_GATEWAY_TOKEN needs ID_HASH_KEY")
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain("tg-token"));
		assertThatCode(() -> StartupGuard.check(prod().withProperty("app.telegram-gateway.token", "tg-token")
			.withProperty("app.identity.hash-key", "a-production-id-hash-key-long-enough-0123456789")))
			.doesNotThrowAnyException();
	}
	
	@Test
	void prodRefusesTheLogSmsBackend() {
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.sms.backend", "log")))
			.hasMessageContaining("SMS_BACKEND=log");
	}
	
	@Test
	void devAndTestAllowTheLogSmsBackend() {
		assertThatCode(() -> StartupGuard.check(env("dev").withProperty("app.jwt.secret", GOOD_SECRET)
			.withProperty("app.sms.backend", "log"))).doesNotThrowAnyException();
	}
	
	@Test
	void refusesAnUnknownSmsBackend() {
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.sms.backend", "twilio")))
			.hasMessageContaining("SMS_BACKEND must be");
	}
	
	@Test
	void httpSmsNeedsUrlKeyAndHashKey() {
		MockEnvironment http = prod().withProperty("app.sms.backend", "http")
			.withProperty("app.sms.http.url", "https://sms.invalid/send")
			.withProperty("app.sms.http.api-key", "not-a-real-key")
			.withProperty("app.identity.hash-key", "an-id-hash-key-that-is-long-enough-0123456789");
		assertThatCode(() -> StartupGuard.check(http)).doesNotThrowAnyException();
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.sms.backend", "http")
			.withProperty("app.identity.hash-key", "an-id-hash-key-that-is-long-enough-0123456789")))
			.hasMessageContaining("SMS_HTTP_URL and SMS_API_KEY");
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.sms.backend", "http")
			.withProperty("app.sms.http.url", "https://sms.invalid/send")
			.withProperty("app.sms.http.api-key", "not-a-real-key")))
			.hasMessageContaining("needs ID_HASH_KEY");
	}
	
	@Test
	void prodRefusesTheAdminRelayWithoutPreviewModeAndWarnsWithIt(CapturedOutput output) {
		String key = "an-id-hash-key-that-is-long-enough-0123456789";
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.sms.backend", "admin-relay")
			.withProperty("app.identity.hash-key", key)))
			.hasMessageContaining("SMS_BACKEND=admin-relay").hasMessageContaining("PREVIEW_MODE=true");
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.sms.backend", "admin-relay")
			.withProperty("app.preview-mode", "true")))
			.hasMessageContaining("needs ID_HASH_KEY");
		assertThatCode(() -> StartupGuard.check(prod().withProperty("app.sms.backend", "admin-relay")
			.withProperty("app.preview-mode", "true").withProperty("app.identity.hash-key", key)))
			.doesNotThrowAnyException();
		assertThat(output).contains("SMS_BACKEND=admin-relay in the 'prod' profile");
	}
	
	@Test
	void devAllowsTheAdminRelayWithoutPreviewModeAndStillWarns(CapturedOutput output) {
		assertThatCode(() -> StartupGuard.check(env("dev").withProperty("app.jwt.secret", GOOD_SECRET)
			.withProperty("app.sms.backend", "admin-relay"))).doesNotThrowAnyException();
		assertThat(output).contains("SMS_BACKEND=admin-relay in the 'dev' profile");
	}
	
	@Test
	void refusesAShortIdHashKey() {
		assertThatThrownBy(() -> StartupGuard.check(prod().withProperty("app.identity.hash-key", "short")))
			.hasMessageContaining("ID_HASH_KEY");
	}
	
	@Test
	void prodStartsWithoutSmsButWarns(CapturedOutput output) {
		assertThatCode(() -> StartupGuard.check(prod())).doesNotThrowAnyException();
		assertThat(output).contains("SMS_BACKEND is empty in the 'prod' profile", "phone_otp_unavailable");
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
