package com.rentrix.rentrixserver.auth;

import com.rentrix.rentrixserver.auth.google.FakeGoogleTokenVerifier;
import com.rentrix.rentrixserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.freshIp;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshCookie;
import static com.rentrix.rentrixserver.auth.AuthTestSupport.refreshToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A refresh that runs while another request is writing (§13, D-11). On SQLite a transaction that reads and then
 * writes fails at once with SQLITE_BUSY when another connection committed a write in between, and the browser then
 * loaded signed out (e2e click-test: submit a listing, open the next page). The rotation must wait for the other
 * write (busy timeout) and succeed.
 */
@IntegrationTest
class RefreshConcurrencyTest {
	
	/** Long enough that the refresh's own write waits on it; well below the 5 s busy timeout. */
	private static final long WRITE_HELD_MS = 1_500;
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	ObjectMapper json;
	
	@Autowired
	DataSource dataSource;
	
	private String signIn() throws Exception {
		String token = FakeGoogleTokenVerifier.token("sub-" + UUID.randomUUID(), "u-" + UUID.randomUUID()
			+ "@example.test", "Asha Rao");
		MockHttpServletResponse response = mvc.perform(post("/auth/google").with(freshIp())
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("idToken", token))))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(200);
		return refreshToken(response);
	}
	
	/** Another connection writes, holds its write lock for a while, then commits. */
	private CompletableFuture<Void> concurrentWrite(CountDownLatch writing) {
		return CompletableFuture.runAsync(() -> {
			try (Connection connection = dataSource.getConnection()) {
				connection.setAutoCommit(false);
				try (Statement statement = connection.createStatement()) {
					statement.executeUpdate("update users set updated_at = updated_at");
					writing.countDown();
					Thread.sleep(WRITE_HELD_MS);
				}
				connection.commit();
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
	}
	
	@Test
	void refreshDuringAnotherWriteWaitsAndRotates() throws Exception {
		String token = signIn();
		CountDownLatch writing = new CountDownLatch(1);
		CompletableFuture<Void> writer = concurrentWrite(writing);
		assertThat(writing.await(10, TimeUnit.SECONDS)).isTrue();
		
		MockHttpServletResponse refreshed = mvc.perform(post("/auth/refresh").cookie(refreshCookie(token)))
			.andReturn().getResponse();
		writer.get(10, TimeUnit.SECONDS);
		
		assertThat(refreshed.getStatus()).as(refreshed.getContentAsString()).isEqualTo(200);
		String next = refreshToken(refreshed);
		assertThat(next).isNotBlank().isNotEqualTo(token);
		// The rotation took: the new token works, the old one is now a grace-window reuse.
		assertThat(mvc.perform(post("/auth/refresh").cookie(refreshCookie(next))).andReturn().getResponse()
			.getStatus()).isEqualTo(200);
	}
	
}
