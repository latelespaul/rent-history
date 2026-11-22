package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.auth.google.FakeGoogleTokenVerifier;
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
 * Behind a reverse proxy (Caddy terminates TLS, forwards to the API on loopback) the browser's
 * {@code Origin} is the public origin. Real Tomcat: {@code X-Forwarded-Proto} / {@code -Host} make that
 * request same-origin only when the direct peer is a trusted proxy ({@code TRUSTED_PROXIES}); the test
 * client connects from loopback. {@code https://preview.example} is never in {@code CORS_ALLOWED_ORIGINS}.
 */
class ProxiedSameOriginCorsTest {

	private static final String PUBLIC_ORIGIN = "https://preview.example";

	abstract static class Base {

		@Autowired
		Environment env;

		private final HttpClient http = HttpClient.newHttpClient();

		int signIn(String origin, boolean forwarded) throws Exception {
			HttpRequest.Builder request = HttpRequest.newBuilder(
					URI.create("http://127.0.0.1:" + env.getProperty("local.server.port") + "/api/v1/auth/google"))
				.header("Content-Type", "application/json")
				.header("Origin", origin)
				.POST(HttpRequest.BodyPublishers.ofString(
					"{\"idToken\":\"" + FakeGoogleTokenVerifier.token("proxy-sub", "proxy@example.com") + "\"}"));
			if (forwarded) {
				request.header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "preview.example");
			}
			return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles("test")
	@DirtiesContext
	class FromATrustedLocalProxy extends Base {

		@Test
		void sameOriginBehindTheProxyIsNotCors() throws Exception {
			assertThat(signIn(PUBLIC_ORIGIN, true)).isEqualTo(200);
		}

		@Test
		void aTrueCrossOriginCallIsStillRefused() throws Exception {
			assertThat(signIn("https://evil.example", true)).isEqualTo(403);
			assertThat(signIn(PUBLIC_ORIGIN, false)).isEqualTo(403);
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "TRUSTED_PROXIES=192\\.0\\.2\\.1")
	@ActiveProfiles("test")
	@DirtiesContext
	class FromAnUntrustedPeer extends Base {

		@Test
		void forwardedHeadersDoNotBypassCors() throws Exception {
			assertThat(signIn(PUBLIC_ORIGIN, true)).isEqualTo(403);
		}

	}

}
