package com.rentrix.rentrixserver.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

/**
 * The privacy-first traffic counter (D-174). It keeps daily totals only: views per normalised path and source, and
 * one salted hash per visitor per day. The salt is random, lives in memory, is replaced when the UTC day changes
 * and is never stored or logged, so a stored hash can be neither reversed nor linked to another day. The IP and the
 * user agent are hashed and forgotten; nothing links a view to a user, a review or an invite code.
 */
@Service
@RequiredArgsConstructor
public class TrafficService {
	
	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();
	private Salt salt;
	
	private record Salt(LocalDate day, byte[] bytes) {
	}
	
	/** One view: bumps the day's counter and notes the visitor once for the day. */
	public void record(String path, String source, String ip, String userAgent) {
		LocalDate day = LocalDate.now(clock.withZone(java.time.ZoneOffset.UTC));
		bump(day, path, source);
		try {
			jdbc.update("INSERT INTO visitor_daily (seen_on, visitor_hash) VALUES (?, ?)", day, hash(day, ip, userAgent));
		}
		catch (DataIntegrityViolationException alreadyCounted) {
			// This visitor was counted today.
		}
	}
	
	private void bump(LocalDate day, String path, String source) {
		String update = "UPDATE page_view_daily SET views = views + 1 WHERE seen_on = ? AND path = ? AND source = ?";
		if (jdbc.update(update, day, path, source) > 0) {
			return;
		}
		try {
			jdbc.update("INSERT INTO page_view_daily (seen_on, path, source, views) VALUES (?, ?, ?, 1)", day, path, source);
		}
		catch (DataIntegrityViolationException raced) {
			jdbc.update(update, day, path, source);
		}
	}
	
	private synchronized String hash(LocalDate day, String ip, String userAgent) {
		if (salt == null || !salt.day().equals(day)) {
			byte[] bytes = new byte[32];
			random.nextBytes(bytes);
			salt = new Salt(day, bytes);
		}
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(salt.bytes());
			sha.update((String.valueOf(ip) + '\n' + String.valueOf(userAgent)).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(sha.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
	
	/** One row of a top list. */
	public record Top(String name, long views) {
	}
	
	/** Views and visitors for one window. A person who comes on two days counts on both (hashes do not link). */
	public record Window(long views, long visitors) {
	}
	
	public record Summary(Window today, Window last7Days, Window last30Days, List<Top> topPages,
		List<Top> topSources) {
	}
	
	/** Totals for today, the last 7 and the last 30 days (today included), and the top 5 pages and sources. */
	public Summary summary() {
		LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneOffset.UTC));
		LocalDate from30 = today.minusDays(29);
		return new Summary(window(today), window(today.minusDays(6)), window(from30), top("path", from30),
			top("source", from30));
	}
	
	private Window window(LocalDate from) {
		Long views = jdbc.queryForObject("SELECT COALESCE(SUM(views), 0) FROM page_view_daily WHERE seen_on >= ?",
			Long.class, from);
		Long visitors = jdbc.queryForObject("SELECT COUNT(*) FROM visitor_daily WHERE seen_on >= ?", Long.class, from);
		return new Window(views == null ? 0 : views, visitors == null ? 0 : visitors);
	}
	
	/** {@code column} is one of two fixed names, never caller input. */
	private List<Top> top(String column, LocalDate from) {
		return jdbc.query("SELECT " + column + " AS name, SUM(views) AS total FROM page_view_daily WHERE seen_on >= ? "
			+ "GROUP BY " + column + " ORDER BY total DESC, " + column + " LIMIT 5",
			(rs, i) -> new Top(rs.getString("name"), rs.getLong("total")), from);
	}
	
}
