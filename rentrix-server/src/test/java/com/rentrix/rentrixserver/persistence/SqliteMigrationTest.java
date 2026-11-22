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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	@Transactional
	void locationHierarchyTablesIndexesAndForeignKeys() {
		List<String> indexes = jdbc.queryForList("select name from sqlite_master where type = 'index'", String.class);
		assertThat(indexes).contains("ix_areas_city_status", "ix_area_pincodes_pincode", "ix_societies_city_status",
			"ix_properties_area_status", "ix_properties_society_status");
		// the inline REFERENCES of V5's ADD COLUMN are real FKs on SQLite (D-114)
		List<String> propertyFks = jdbc.queryForList("select \"table\" || '.' || \"from\" from pragma_foreign_key_list('properties')",
			String.class);
		assertThat(propertyFks).contains("areas.area_id", "societies.society_id", "society_towers.tower_id",
			"society_wings.wing_id");
		jdbc.update("insert into users (id, public_id, display_name, status, is_demo, created_at, updated_at) values"
			+ " (9007199254740002, '00000000-0000-7000-8000-00000000fk02', 'FK Landlord', 'ACTIVE', false,"
			+ " current_timestamp, current_timestamp)");
		String insert = "insert into properties (id, public_id, landlord_id, city_code, locality, locality_key, pincode,"
			+ " property_type, status, is_demo, created_at, updated_at, area_id) values (?, ?, 9007199254740002, 'BLR',"
			+ " 'X', 'x', '560034', 'APARTMENT', 'DRAFT', false, current_timestamp, current_timestamp, ?)";
		assertThat(jdbc.update(insert, 9007199254740001L, "00000000-0000-7000-8000-00000000fk01", null)).isEqualTo(1);
		assertThatThrownBy(() -> jdbc.update(insert, 9007199254740003L, "00000000-0000-7000-8000-00000000fk03",
			9007199254740999L))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
	
	@Test
	void searchAndRatingIndexesExist() {
		List<String> indexes = jdbc.queryForList("select name from sqlite_master where type = 'index'", String.class);
		assertThat(indexes).contains("ix_properties_city_status_locality", "ix_reviews_subject_user",
			"ix_reviews_property", "ix_rating_aggregates_rank", "ix_tenancies_pair");
	}
	
}
