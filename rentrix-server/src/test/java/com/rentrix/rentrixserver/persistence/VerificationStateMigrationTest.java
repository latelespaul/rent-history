package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.config.MigrationPlaceholders;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7 (D-134, D-135) on a database that already has rating rows from published reviews, as on a deployment that
 * ran B6 before this migration: the rows stay as they are (the headline mode decides counting, D-135).
 */
class VerificationStateMigrationTest {
	
	@Test
	void existingAggregatesStayUnchanged(@TempDir Path dir) {
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:" + dir.resolve("v7.db"));
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		
		flyway(dataSource, "6").migrate();
		jdbc.update("insert into rating_aggregates (subject_type, subject_id, review_count, rating_sum, average,"
			+ " bayes_score, count_4, count_5, sub_a_avg, last_review_at, created_at, updated_at)"
			+ " values ('PROPERTY', 42, 2, 9, 4.50, 3.786, 1, 1, 4.00, '2026-01-02 00:00:00.000',"
			+ " '2026-01-01 00:00:00.000', '2026-01-02 00:00:00.000')");
		Map<String, Object> before = jdbc.queryForMap("select * from rating_aggregates where subject_id = 42");
		
		assertThat(flyway(dataSource, "latest").migrate().success).isTrue();
		Map<String, Object> after = jdbc.queryForMap("select * from rating_aggregates where subject_id = 42");
		assertThat(after).isEqualTo(before);
		assertThat(after).containsEntry("review_count", 2).containsEntry("rating_sum", 9);
	}
	
	private static Flyway flyway(SQLiteDataSource dataSource, String target) {
		return Flyway.configure()
			.dataSource(dataSource)
			.locations("classpath:db/migration", "classpath:db/vendor/sqlite")
			.placeholders(MigrationPlaceholders.forProduct("SQLite"))
			.target(target)
			.load();
	}
	
}
