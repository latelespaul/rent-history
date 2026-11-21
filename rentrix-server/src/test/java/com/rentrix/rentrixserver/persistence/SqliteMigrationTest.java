package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.config.RentrixSqliteDialect;
import com.rentrix.rentrixserver.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

/**
 * The test context itself proves the main point: it only starts when the real Flyway
 * migrations ran on SQLite and Hibernate validated every entity against them.
 */
@IntegrationTest
class SqliteMigrationTest {
	
	@Autowired
	Flyway flyway;
	
	@Autowired
	JdbcTemplate jdbc;
	
	@Autowired
	EntityManagerFactory emf;
	
	@Test
	void allMigrationsAppliedOnSqlite() {
		assertThat(jdbc.queryForObject("select sqlite_version()", String.class)).isNotBlank();
		MigrationInfo[] applied = flyway.info().applied();
		assertThat(applied).isNotEmpty();
		assertThat(Arrays.stream(applied).map(MigrationInfo::getState)).containsOnly(MigrationState.SUCCESS);
		assertThat(flyway.info().pending()).isEmpty();
	}
	
	@Test
	void sqliteRunsWithForeignKeysWalAndBusyTimeout() {
		assertThat(jdbc.queryForObject("pragma foreign_keys", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("pragma journal_mode", String.class)).isEqualToIgnoringCase("wal");
		assertThat(jdbc.queryForObject("pragma busy_timeout", Integer.class)).isEqualTo(5000);
	}
	
	@Test
	void sqliteUsesTheDialectThatMapsConstraintErrors() {
		assertThat(emf.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect())
			.isInstanceOf(RentrixSqliteDialect.class);
	}
	
	@Test
	void seedCitiesAreLiveWithPincodePrefixes() {
		List<String> live = jdbc.queryForList(
			"select code || ':' || pincode_prefixes from cities where live = true order by code", String.class);
		assertThat(live).containsExactly("BLR:56", "PNQ:41");
	}
	
	@Test
	void searchAndRatingIndexesExist() {
		List<String> indexes = jdbc.queryForList("select name from sqlite_master where type = 'index'", String.class);
		assertThat(indexes).contains("ix_properties_city_status_locality", "ix_reviews_subject_user",
			"ix_reviews_property", "ix_rating_aggregates_rank", "ix_tenancies_pair");
	}
	
}
