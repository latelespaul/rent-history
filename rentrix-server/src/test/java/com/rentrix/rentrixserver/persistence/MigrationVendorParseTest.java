package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.config.MigrationPlaceholders;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
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
		
		// the same locations as spring.flyway.locations, with {vendor} resolved (db/vendor/postgresql has no files)
		MigrateResult result = Flyway.configure()
			.dataSource(dataSource)
			.locations("classpath:db/migration", "classpath:db/vendor/" + product.toLowerCase(Locale.ROOT))
			.placeholders(MigrationPlaceholders.forProduct(product))
			.load()
			.migrate();
		
		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isPositive();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select count(*) from cities where live = true", Integer.class)).isEqualTo(7);
		assertThat(jdbc.queryForObject("select pincode_prefixes from cities where code = 'BLR'", String.class))
			.isEqualTo("56");
		// V5 location hierarchy, plus the MySQL-only V5.1 foreign keys (D-114); V11 seeds the launch-city PINs
		// (58), V18 those of Bangalore and Pune (174) and V23 more of the other five cities (109)
		assertThat(jdbc.queryForObject("select count(*) from area_pincodes", Integer.class)).isEqualTo(341);
		boolean mysql = product.equals("MySQL");
		List<String> versions = result.migrations.stream().map(m -> m.version).toList();
		assertThat(versions).contains("5");
		assertThat(versions.contains("5.1")).as("5.1 only on MySQL").isEqualTo(mysql);
		assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints"
			+ " where lower(constraint_name) = 'fk_properties_area'", Integer.class)).isEqualTo(mysql ? 1 : 0);
		// V7 verification state (D-134), plus the MySQL-only V7.1 foreign keys
		assertThat(versions).contains("7");
		assertThat(versions.contains("7.1")).as("7.1 only on MySQL").isEqualTo(mysql);
		assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints"
			+ " where lower(constraint_name) = 'fk_reviews_verification_decided_by'", Integer.class))
			.isEqualTo(mysql ? 1 : 0);
		// V16 review form (D-157): parameter ratings with a real FK on every vendor, and the new review columns
		assertThat(versions).contains("16");
		assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints"
			+ " where lower(constraint_name) = 'fk_review_criterion_ratings_review'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where lower(table_name) ="
			+ " 'reviews' and lower(column_name) in ('would_recommend', 'pros', 'cons')", Integer.class)).isEqualTo(3);
		// V17 the form review marker (D-157)
		assertThat(versions).contains("17");
		assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where lower(table_name) ="
			+ " 'reviews' and lower(column_name) = 'form_review'", Integer.class)).isEqualTo(1);
	}
	
	@ParameterizedTest(name = "{0}")
	@CsvSource({"MySQL, DATETIME(6)", "MariaDB, DATETIME(6)", "PostgreSQL, TIMESTAMP", "SQLite, TIMESTAMP"})
	void timestampTypePerVendor(String product, String expected) {
		assertThat(MigrationPlaceholders.forProduct(product)).containsEntry(MigrationPlaceholders.TIMESTAMP_TYPE,
			expected);
	}
	
}
