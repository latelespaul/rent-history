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
		assertThat(live).containsExactly("BLR:56", "DEL:11", "GGN:122", "HYD:500,501,502", "MUM:400,401", "NOI:201", "PNQ:41");
		List<String> regions = jdbc.queryForList(
			"select code from cities where region = 'Delhi NCR' order by code", String.class);
		assertThat(regions).containsExactly("DEL", "GGN", "NOI");
	}
	
	@Test
	void tenancyRentIsOptionalAfterTheRebuildAndItsKeysStillHold() {
		// V12 (sqlite vendor file) rebuilt tenancies with initial_rent_inr nullable (captain 2026-11-23 09:55)
		assertThat(jdbc.queryForObject("select \"notnull\" from pragma_table_info('tenancies') where name = 'initial_rent_inr'",
			Integer.class)).isZero();
		assertThat(jdbc.queryForList("select name from sqlite_master where type = 'index' and tbl_name = 'tenancies'"
			+ " and name like 'ix_%'", String.class)).containsExactlyInAnyOrder("ix_tenancies_property_status",
			"ix_tenancies_tenant_status", "ix_tenancies_landlord_status", "ix_tenancies_pair", "ix_tenancies_verification");
		assertThat(jdbc.queryForList("select \"table\" from pragma_foreign_key_list('reviews')", String.class))
			.contains("tenancies");
		assertThat(jdbc.queryForList("pragma foreign_key_check", Object.class)).isEmpty();
	}
	
	@Test
	void launchCitiesHaveActiveAreasWhosePincodesMatchTheCity() {
		// V11: 8 - 12 well-known localities in each city that went live in V8 (#53 click-test bug 3)
		List<String> counts = jdbc.queryForList("select city_code || ':' || count(*) from areas"
			+ " where is_demo = false and status = 'ACTIVE' and created_by is null and id between 101 and 155"
			+ " and city_code in ('DEL', 'GGN', 'HYD', 'MUM', 'NOI') group by city_code order by city_code", String.class);
		assertThat(counts).containsExactly("DEL:12", "GGN:10", "HYD:11", "MUM:12", "NOI:10");
		Integer unpinned = jdbc.queryForObject("select count(*) from areas a where a.id between 101 and 155"
			+ " and not exists (select 1 from area_pincodes p where p.area_id = a.id)", Integer.class);
		assertThat(unpinned).isZero();
		List<String> offPrefix = jdbc.queryForList("select a.name || ' ' || p.pincode from areas a"
			+ " join area_pincodes p on p.area_id = a.id join cities c on c.code = a.city_code"
			+ " where a.id between 101 and 155 and not ((',' || c.pincode_prefixes || ',') like ('%,' || substr(p.pincode, 1, 2) || ',%')"
			+ " or (',' || c.pincode_prefixes || ',') like ('%,' || substr(p.pincode, 1, 3) || ',%'))", String.class);
		assertThat(offPrefix).isEmpty();
		assertThat(jdbc.queryForList("select name from areas where city_code = 'HYD' and name_key = 'gachibowli'",
			String.class)).containsExactly("Gachibowli");
	}
	
	@Test
	void bangaloreAndPuneHaveActiveAreasWithSixDigitPincodesOfTheCity() {
		// V18: main residential localities of BLR and PNQ, the only live cities, with India Post PIN codes
		List<String> counts = jdbc.queryForList("select city_code || ':' || count(*) from areas"
			+ " where is_demo = false and status = 'ACTIVE' and created_by is null and id between 201 and 381"
			+ " group by city_code order by city_code", String.class);
		assertThat(counts).containsExactly("BLR:82", "PNQ:81");
		Integer unpinned = jdbc.queryForObject("select count(*) from areas a where a.id between 201 and 381"
			+ " and not exists (select 1 from area_pincodes p where p.area_id = a.id)", Integer.class);
		assertThat(unpinned).isZero();
		List<String> badPins = jdbc.queryForList("select a.name || ' ' || p.pincode from areas a"
			+ " join area_pincodes p on p.area_id = a.id where a.id between 201 and 381"
			+ " and (length(p.pincode) <> 6 or p.pincode glob '*[^0-9]*'"
			+ " or (a.city_code = 'BLR' and p.pincode not like '560%')"
			+ " or (a.city_code = 'PNQ' and substr(p.pincode, 1, 3) not in ('410', '411', '412')))", String.class);
		assertThat(badPins).isEmpty();
		assertThat(jdbc.queryForList("select p.pincode from areas a join area_pincodes p on p.area_id = a.id"
			+ " where a.city_code = 'BLR' and a.name_key = 'koramangala' order by p.pincode", String.class))
			.containsExactly("560034", "560095");
	}
	
	@Test
	void otherLiveCitiesHaveMoreIndiaPostAreasWithPincodesOfTheCity() {
		// V23 (captain 2026-11-28): more localities of HYD, GGN, NOI, MUM and DEL, so typing an area suggests it
		List<String> counts = jdbc.queryForList("select city_code || ':' || count(*) from areas"
			+ " where is_demo = false and status = 'ACTIVE' and created_by is null and id between 401 and 509"
			+ " group by city_code order by city_code", String.class);
		assertThat(counts).containsExactly("DEL:29", "GGN:11", "HYD:29", "MUM:31", "NOI:9");
		Integer unpinned = jdbc.queryForObject("select count(*) from areas a where a.id between 401 and 509"
			+ " and not exists (select 1 from area_pincodes p where p.area_id = a.id)", Integer.class);
		assertThat(unpinned).isZero();
		List<String> offPrefix = jdbc.queryForList("select a.name || ' ' || p.pincode from areas a"
			+ " join area_pincodes p on p.area_id = a.id join cities c on c.code = a.city_code"
			+ " where a.id between 401 and 509 and (length(p.pincode) <> 6 or p.pincode glob '*[^0-9]*' or not ("
			+ "(',' || c.pincode_prefixes || ',') like ('%,' || substr(p.pincode, 1, 2) || ',%')"
			+ " or (',' || c.pincode_prefixes || ',') like ('%,' || substr(p.pincode, 1, 3) || ',%')))", String.class);
		assertThat(offPrefix).isEmpty();
		assertThat(jdbc.queryForList("select a.name || ' ' || p.pincode from areas a join area_pincodes p"
			+ " on p.area_id = a.id where a.city_code = 'MUM' and a.name_key = 'vile parle west'", String.class))
			.containsExactly("Vile Parle West 400056");
		// a V11 locality is not added twice
		assertThat(jdbc.queryForObject("select count(*) from areas where city_code = 'HYD' and name_key = 'kondapur'",
			Integer.class)).isOne();
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
	
	@Test
	void reviewParameterRatingsReferenceTheirReview() {
		// V16 (D-157): one row per rated parameter, keyed by review and parameter
		assertThat(jdbc.queryForList("select \"table\" from pragma_foreign_key_list('review_criterion_ratings')",
			String.class)).containsExactly("reviews");
		assertThat(jdbc.queryForList("select name from pragma_table_info('review_criterion_ratings') where pk > 0"
			+ " order by pk", String.class)).containsExactly("review_id", "criterion");
	}
	
}
