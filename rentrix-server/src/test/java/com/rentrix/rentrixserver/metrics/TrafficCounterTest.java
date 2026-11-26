package com.rentrix.rentrixserver.metrics;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.rentrix.rentrixserver.support.AdminConsole.adminBearer;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The traffic counter (D-174): daily aggregates, normalised paths and sources, and no IP or user agent kept. */
@IntegrationTest
@Transactional
class TrafficCounterTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	JdbcTemplate jdbc;
	
	@Autowired
	TestData data;
	
	@Autowired
	AccessTokenService tokens;
	
	private void view(String path, String referrer, String source, String ip, String agent) throws Exception {
		RequestPostProcessor from = request -> {
			request.setRemoteAddr(ip);
			return request;
		};
		String body = "{\"path\":\"" + path + "\",\"referrer\":" + quote(referrer) + ",\"source\":" + quote(source) + "}";
		mvc.perform(post("/metrics/pageview").with(from).header(HttpHeaders.USER_AGENT, agent)
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNoContent());
	}
	
	private static String quote(String s) {
		return s == null ? "null" : "\"" + s + "\"";
	}
	
	private long views(String path, String source) {
		Long n = jdbc.queryForObject("SELECT COALESCE(SUM(views), 0) FROM page_view_daily WHERE path = ? AND source = ?",
			Long.class, path, source);
		return n == null ? 0 : n;
	}
	
	@Test
	void countsViewsPerPathAndSourceAndVisitorsOncePerDay() throws Exception {
		view("/search", "https://www.google.com/search?q=x", null, "10.1.1.1", "UA-1");
		view("/search", "https://www.google.com/", null, "10.1.1.1", "UA-1");
		view("/search", "", null, "10.1.1.2", "UA-2");
		assertThat(views("/search", "google")).isEqualTo(2);
		assertThat(views("/search", "direct")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visitor_daily", Long.class)).isEqualTo(2);
	}
	
	@Test
	void pathsLoseIdsAndQueries() throws Exception {
		view("/p/0198f3a2-7b1c-7000-8000-123456789abc?utm=1#top", null, null, "10.2.2.2", "UA");
		view("/homes/abc123/edit", null, null, "10.2.2.2", "UA");
		view("/some/unknown/probe", null, null, "10.2.2.2", "UA");
		assertThat(views("/p/{id}", "direct")).isEqualTo(1);
		assertThat(views("/homes/{id}/edit", "direct")).isEqualTo(1);
		assertThat(views("/other", "direct")).isEqualTo(1);
		List<String> paths = jdbc.queryForList("SELECT path FROM page_view_daily", String.class);
		assertThat(paths).noneMatch(p -> p.contains("0198") || p.contains("?") || p.contains("abc123"));
	}
	
	@Test
	void sourcesAreCoarseAndNeverCarryARefCode() throws Exception {
		view("/", "https://wa.me/", "invite", "10.3.3.3", "UA");
		view("/", "https://l.facebook.com/l.php?u=1", null, "10.3.3.3", "UA");
		view("/", "https://news.example.org/story/42?id=7", null, "10.3.3.3", "UA");
		view("/", "http://localhost/search", null, "10.3.3.3", "UA");
		view("/", null, "Newsletter", "10.3.3.3", "UA");
		List<String> sources = jdbc.queryForList("SELECT source FROM page_view_daily", String.class);
		assertThat(sources).containsExactlyInAnyOrder("invite", "facebook", "news.example.org", "internal", "newsletter");
	}
	
	@Test
	void noIpOrUserAgentIsStored() throws Exception {
		view("/search", "https://example.org/a?b=c", null, "203.0.113.77", "SecretAgent/9.9");
		StringBuilder all = new StringBuilder();
		for (String table : List.of("page_view_daily", "visitor_daily")) {
			for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + table)) {
				all.append(row.toString());
			}
			List<String> columns = jdbc.query("SELECT * FROM " + table + " WHERE 1 = 0",
				rs -> {
					List<String> names = new java.util.ArrayList<>();
					for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
						names.add(rs.getMetaData().getColumnName(i).toLowerCase());
					}
					return names;
				});
			assertThat(columns).doesNotContain("user_id", "ip", "ip_address", "user_agent");
		}
		assertThat(all.toString()).doesNotContain("203.0.113.77").doesNotContain("SecretAgent").doesNotContain("/a?b=c");
		String hash = jdbc.queryForObject("SELECT visitor_hash FROM visitor_daily", String.class);
		assertThat(hash).matches("[0-9a-f]{64}");
	}
	
	@Test
	void rateLimitsOneAddress() throws Exception {
		for (int i = 0; i < PageViewRateLimiter.REQUESTS; i++) {
			view("/", null, null, "10.9.9.9", "UA");
		}
		mvc.perform(post("/metrics/pageview").with(request -> {
				request.setRemoteAddr("10.9.9.9");
				return request;
			}).contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/\"}"))
			.andExpect(status().isTooManyRequests());
	}
	
	@Test
	void rejectsAHugeBody() throws Exception {
		mvc.perform(post("/metrics/pageview").contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\":\"/" + "a".repeat(500) + "\"}"))
			.andExpect(status().isUnprocessableContent());
	}
	
	@Test
	void adminSummaryNeedsAnAdminAndAddsUp() throws Exception {
		mvc.perform(get("/admin/traffic").with(adminHost())).andExpect(status().isUnauthorized());
		view("/search", "https://t.me/x", null, "10.4.4.4", "UA");
		view("/search", "https://t.me/x", null, "10.4.4.5", "UA");
		view("/login", null, null, "10.4.4.5", "UA");
		User admin = data.user("Traffic Admin " + UUID.randomUUID(), Role.ADMIN);
		mvc.perform(get("/admin/traffic").with(adminHost()).header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.today.views").value(3))
			.andExpect(jsonPath("$.today.visitors").value(2))
			.andExpect(jsonPath("$.last7Days.views").value(3))
			.andExpect(jsonPath("$.last30Days.views").value(3))
			.andExpect(jsonPath("$.topPages[0].name").value("/search"))
			.andExpect(jsonPath("$.topPages[0].views").value(2))
			.andExpect(jsonPath("$.topSources[0].name").value("telegram"));
	}
	
}
