package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.config.MigrationPlaceholders;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

/**
 * Runs the real migrations on H2 in MySQL and PostgreSQL compatibility mode, with the
 * placeholders the API would use on that vendor. A fast, Docker-free check that the SQL
 * stays inside the portable subset (D-10). {@link VendorDatabaseContainerTest} runs the same
 * migrations on real MySQL and PostgreSQL when Docker is available (CI).
 */
class MigrationVendorParseTest {
	
	@ParameterizedTest(name = "{0}")
	@CsvSource({
		"MySQL, MySQL, ;DATABASE_TO_LOWER=TRUE",
		"PostgreSQL, PostgreSQL, ;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
	})
	void migrationsRunInCompatibilityMode(String mode, String product, String extra) {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=" + mode + extra + ";DB_CLOSE_DELAY=-1");
		
		MigrateResult result = Flyway.configure()
			.dataSource(dataSource)
			.locations("classpath:db/migration")
			.placeholders(MigrationPlaceholders.forProduct(product))
			.load()
			.migrate();
		
		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isPositive();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select count(*) from cities where live = true", Integer.class)).isEqualTo(2);
	}
	
	@ParameterizedTest(name = "{0}")
	@CsvSource({"MySQL, DATETIME(6)", "MariaDB, DATETIME(6)", "PostgreSQL, TIMESTAMP", "SQLite, TIMESTAMP"})
	void timestampTypePerVendor(String product, String expected) {
		assertThat(MigrationPlaceholders.forProduct(product)).containsEntry(MigrationPlaceholders.TIMESTAMP_TYPE,
			expected);
	}
	
}
