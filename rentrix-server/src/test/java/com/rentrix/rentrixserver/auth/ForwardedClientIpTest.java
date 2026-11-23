package com.rentrix.rentrixserver.auth;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The {@code prod} profile behind real Tomcat: {@code X-Forwarded-For} picks the rate-limit IP only when the
 * direct peer is a trusted proxy ({@code TRUSTED_PROXIES}); the test client connects from loopback.
 */
class ForwardedClientIpTest {
	
	private static final String PROD = "spring.datasource.url=jdbc:sqlite:target/test-data/rentrix-prod-${random.uuid}.db";
	private static final String SECRET = "app.jwt.secret=prod-profile-test-secret-that-is-long-enough-0123456789";
	private static final String DOCUMENT_KEY = "app.documents.encryption-key=prod-profile-test-document-key-long-enough-0123456789";
	private static final String LIMIT = "app.auth.rate-limit.requests=3";
	
	abstract static class Base {
		
		@Autowired
		Environment env;
		
		private final HttpClient http = HttpClient.newHttpClient();
		
		int signIn(String forwardedFor) throws Exception {
			HttpRequest request = HttpRequest.newBuilder(
					URI.create("http://127.0.0.1:" + env.getProperty("local.server.port") + "/api/v1/auth/google"))
				.header("Content-Type", "application/json")
				.header("X-Forwarded-For", forwardedFor)
				.POST(HttpRequest.BodyPublishers.ofString("{\"idToken\":\"x\"}"))
				.build();
			return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
		}
		
	}
	
	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {PROD, SECRET, DOCUMENT_KEY, LIMIT, "TRUSTED_PROXIES=192\\.0\\.2\\.1"})
	@ActiveProfiles("prod")
	@DirtiesContext
	class FromAnUntrustedPeer extends Base {
		
		@Test
		void spoofedForwardedForDoesNotGetAFreshBucket() throws Exception {
			for (int i = 1; i <= 3; i++) {
				assertThat(signIn("203.0.113." + i)).isEqualTo(503);
			}
			assertThat(signIn("203.0.113.99")).isEqualTo(429);
		}
		
	}
	
	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {PROD, SECRET, DOCUMENT_KEY, LIMIT})
	@ActiveProfiles("prod")
	@DirtiesContext
	class FromATrustedLocalProxy extends Base {
		
		@Test
		void forwardedForIsTheClientIp() throws Exception {
			for (int i = 1; i <= 3; i++) {
				assertThat(signIn("198.51.100.7")).isEqualTo(503);
			}
			assertThat(signIn("198.51.100.7")).isEqualTo(429);
			assertThat(signIn("198.51.100.8")).isEqualTo(503);
		}
		
	}
	
}
