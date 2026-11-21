package com.rentrix.rentrixserver.common.idempotency;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/** {@code Idempotency-Key} on {@link Idempotent} endpoints (§12.1), on the real V2 table. */
@IntegrationTest
class IdempotencyTest {
	
	private static final String ENDPOINT = "/test-only/idempotent";
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	AccessTokenService tokens;
	
	@Autowired
	IdempotencyStore store;
	
	@Autowired
	IdempotencyCleanupJob cleanupJob;
	
	@Autowired
	JdbcTemplate jdbc;
	
	private final UUID user = UUID.randomUUID();
	
	private String bearer(UUID who) {
		return "Bearer " + tokens.issue(who, Set.of(Role.TENANT));
	}
	
	private MvcResult send(UUID who, String key, String body) throws Exception {
		var request = post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(who))
			.contentType(MediaType.APPLICATION_JSON).content(body);
		if (key != null) {
			request.header(IdempotencyFilter.HEADER, key);
		}
		return mvc.perform(request).andReturn();
	}
	
	private static String key() {
		return "key-" + UUID.randomUUID();
	}
	
	private static String body(String name) {
		return "{\"name\":\"" + name + "\",\"rating\":4}";
	}
	
	@Test
	void sameKeyAndRequestReplaysTheFirstResponseWithoutWritingAgain() throws Exception {
		String key = key();
		MvcResult first = send(user, key, body("a"));
		assertThat(first.getResponse().getStatus()).isEqualTo(201);
		assertThat(first.getResponse().getHeader(IdempotencyFilter.REPLAYED_HEADER)).isNull();
		
		MvcResult replay = send(user, key, body("a"));
		assertThat(replay.getResponse().getStatus()).isEqualTo(201);
		assertThat(replay.getResponse().getHeader(IdempotencyFilter.REPLAYED_HEADER)).isEqualTo("true");
		assertThat(replay.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
		
		MvcResult fresh = send(user, key(), body("a"));
		assertThat(fresh.getResponse().getContentAsString()).isNotEqualTo(first.getResponse().getContentAsString());
	}
	
	@Test
	void sameKeyWithADifferentRequestIs422() throws Exception {
		String key = key();
		send(user, key, body("a"));
		mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(user))
				.header(IdempotencyFilter.HEADER, key)
				.contentType(MediaType.APPLICATION_JSON).content(body("b")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("idempotency_key_reused"));
	}
	
	@Test
	void keysAreScopedPerCaller() throws Exception {
		String key = key();
		MvcResult mine = send(user, key, body("a"));
		MvcResult theirs = send(UUID.randomUUID(), key, body("a"));
		assertThat(theirs.getResponse().getStatus()).isEqualTo(201);
		assertThat(theirs.getResponse().getHeader(IdempotencyFilter.REPLAYED_HEADER)).isNull();
		assertThat(theirs.getResponse().getContentAsString()).isNotEqualTo(mine.getResponse().getContentAsString());
	}
	
	@Test
	void failedRequestReleasesTheKey() throws Exception {
		String key = key();
		assertThat(send(user, key, body("fail")).getResponse().getStatus()).isEqualTo(422);
		assertThat(store.findLive(user.toString(), key)).isEmpty();
		assertThat(send(user, key, body("a")).getResponse().getStatus()).isEqualTo(201);
	}
	
	@Test
	void invalidRequestBodyDoesNotKeepTheKey() throws Exception {
		String key = key();
		assertThat(send(user, key, "{\"name\":\"\",\"rating\":4}").getResponse().getStatus()).isEqualTo(422);
		assertThat(store.findLive(user.toString(), key)).isEmpty();
	}
	
	@Test
	void keyStillInProgressIs409() throws Exception {
		String key = key();
		send(user, key, body("a"));
		// as if the first request were still running on another instance
		jdbc.update("update idempotency_keys set state = 'IN_PROGRESS', response_body = null where idempotency_key = ?",
			key);
		mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(user))
				.header(IdempotencyFilter.HEADER, key)
				.contentType(MediaType.APPLICATION_JSON).content(body("a")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("idempotency_request_in_progress"));
	}
	
	@Test
	void malformedKeyIs400() throws Exception {
		mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(user))
				.header(IdempotencyFilter.HEADER, "has spaces and is far too long ".repeat(3))
				.contentType(MediaType.APPLICATION_JSON).content(body("a")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("idempotency_key_invalid"));
	}
	
	@Test
	void withoutAKeyEveryRequestWrites() throws Exception {
		String first = send(user, null, body("a")).getResponse().getContentAsString();
		String second = send(user, null, body("a")).getResponse().getContentAsString();
		assertThat(second).isNotEqualTo(first);
	}
	
	@Test
	void endpointsWithoutTheAnnotationIgnoreTheHeader() throws Exception {
		String key = key();
		mvc.perform(post("/test-only/not-idempotent").header(HttpHeaders.AUTHORIZATION, bearer(user))
				.header(IdempotencyFilter.HEADER, key)
				.contentType(MediaType.APPLICATION_JSON).content(body("a")))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));
		assertThat(store.findLive(user.toString(), key)).isEmpty();
	}
	
	@Test
	void expiredKeysAreForgottenAndPurged() throws Exception {
		String key = key();
		String first = send(user, key, body("a")).getResponse().getContentAsString();
		expire(key);
		MvcResult again = send(user, key, body("a"));
		assertThat(again.getResponse().getHeader(IdempotencyFilter.REPLAYED_HEADER)).isNull();
		assertThat(again.getResponse().getContentAsString()).isNotEqualTo(first);
		
		String other = key();
		send(user, other, body("a"));
		expire(other);
		cleanupJob.run();
		assertThat(count(other)).isZero();
		assertThat(count(key)).isOne();
	}
	
	@Test
	void storedRowKeepsTheResponseFor24Hours() throws Exception {
		String key = key();
		send(user, key, body("a"));
		IdempotencyRecord record = store.findLive(user.toString(), key).orElseThrow();
		assertThat(record.getState()).isEqualTo(IdempotencyState.COMPLETED);
		assertThat(record.getResponseStatus()).isEqualTo(201);
		assertThat(record.getRequestHash()).hasSize(64);
		assertThat(record.getExpiresAt()).isBetween(Instant.now().plus(23, ChronoUnit.HOURS),
			Instant.now().plus(25, ChronoUnit.HOURS));
	}
	
	private void expire(String key) {
		jdbc.update("update idempotency_keys set expires_at = ? where idempotency_key = ?",
			Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)), key); // 2 days: safe for any JVM time zone
	}
	
	private int count(String key) {
		return jdbc.queryForObject("select count(*) from idempotency_keys where idempotency_key = ?", Integer.class, key);
	}
	
}
