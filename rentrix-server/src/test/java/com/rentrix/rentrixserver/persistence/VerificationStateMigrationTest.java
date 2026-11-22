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
 * ran B6 before this migration: the reviews read as unverified, so the headline rows drop to zero, once.
 */
class VerificationStateMigrationTest {
	
	@Test
	void existingAggregatesDropToZeroAndTheNewColumnsReadAsUnverified(@TempDir Path dir) {
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:" + dir.resolve("v7.db"));
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		
		flyway(dataSource, "6").migrate();
		jdbc.update("insert into rating_aggregates (subject_type, subject_id, review_count, rating_sum, average,"
			+ " bayes_score, count_4, count_5, sub_a_avg, last_review_at, created_at, updated_at)"
			+ " values ('PROPERTY', 42, 2, 9, 4.50, 3.786, 1, 1, 4.00, '2026-01-02 00:00:00.000',"
			+ " '2026-01-01 00:00:00.000', '2026-01-02 00:00:00.000')");
		
		assertThat(flyway(dataSource, "latest").migrate().success).isTrue();
		Map<String, Object> row = jdbc.queryForMap("select * from rating_aggregates where subject_id = 42");
		assertThat(row).containsEntry("review_count", 0).containsEntry("rating_sum", 0)
			.containsEntry("count_4", 0).containsEntry("count_5", 0);
		assertThat(row.get("average")).isNull();
		assertThat(row.get("bayes_score")).isNull();
		assertThat(row.get("sub_a_avg")).isNull();
		assertThat(row.get("last_review_at")).isNull();
		// The row survives, so later recomputes and the "Top rated" sort keep working.
		assertThat(jdbc.queryForObject("select count(*) from rating_aggregates", Integer.class)).isEqualTo(1);
		
		// Running Flyway again changes nothing: the reset ran once, with its migration.
		jdbc.update("update rating_aggregates set review_count = 1, rating_sum = 5 where subject_id = 42");
		assertThat(flyway(dataSource, "latest").migrate().migrationsExecuted).isZero();
		assertThat(jdbc.queryForObject("select review_count from rating_aggregates where subject_id = 42",
			Integer.class)).isEqualTo(1);
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
