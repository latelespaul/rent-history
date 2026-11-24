package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.config.MigrationPlaceholders;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V18 (captain 2026-11-24) on a database that already has BLR and PNQ areas, as LIVE (one user's PENDING
 * proposal) and dev (the demo seed's rows) do: an ACTIVE or demo place is never added twice, under its own name,
 * one of the new row's aliases, or an alias of the existing row, and those rows stay as they are. A PENDING or
 * HIDDEN row does not hide the reference locality: it is added next to it, or, when the row has the locality's
 * exact name, made ACTIVE in place with the reference aliases and PIN codes.
 */
class CityAreasMigrationTest {

	private static final String ROW = "insert into areas (id, public_id, city_code, name, name_key, aliases, status,"
		+ " created_by, is_demo, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?,"
		+ " '2026-11-20 00:00:00.000', '2026-11-20 00:00:00.000')";

	private static final String PIN = "insert into area_pincodes (area_id, pincode, created_at, updated_at)"
		+ " values (?, ?, '2026-11-20 00:00:00.000', '2026-11-20 00:00:00.000')";

	private static final long PROPOSER = 9007199254740001L;

	@Test
	void skipsActiveAndDemoPlacesAndActivatesPendingOrHiddenOnes(@TempDir Path dir) {
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:" + dir.resolve("v18.db"));
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		flyway(dataSource, "17").migrate();
		jdbc.update("insert into users (id, public_id, display_name, status, is_demo, created_at, updated_at) values"
			+ " (?, '00000000-0000-7000-8000-000000000100', 'Proposer', 'ACTIVE', false, current_timestamp,"
			+ " current_timestamp)", PROPOSER);
		// a user's PENDING proposal named like an alias of Koramangala
		jdbc.update(ROW, 9007199254740101L, "00000000-0000-7000-8000-000000000101", "BLR", "Kormangala",
			"kormangala", null, "PENDING", PROPOSER, false);
		// demo rows: Baner by its own name, Whitefield through the alias of another row
		jdbc.update(ROW, 9007199254740102L, "00000000-0000-7000-8000-000000000102", "PNQ", "Baner", "baner", null,
			"ACTIVE", null, true);
		jdbc.update(PIN, 9007199254740102L, "411045");
		jdbc.update(ROW, 9007199254740103L, "00000000-0000-7000-8000-000000000103", "BLR", "ITPL", "itpl",
			"Whitefield", "ACTIVE", null, true);
		// a user's PENDING proposal with Indiranagar's exact name, and a HIDDEN Jayanagar that has one of its PINs
		jdbc.update(ROW, 9007199254740104L, "00000000-0000-7000-8000-000000000104", "BLR", "indiranagar",
			"indiranagar", null, "PENDING", PROPOSER, false);
		jdbc.update(PIN, 9007199254740104L, "560038");
		jdbc.update(ROW, 9007199254740105L, "00000000-0000-7000-8000-000000000105", "BLR", "Jayanagar", "jayanagar",
			null, "HIDDEN", null, false);
		jdbc.update(PIN, 9007199254740105L, "560011");
		String kept = "select * from areas where id in (9007199254740101, 9007199254740102, 9007199254740103)"
			+ " order by id";
		List<Map<String, Object>> before = jdbc.queryForList(kept);

		assertThat(flyway(dataSource, "latest").migrate().success).isTrue();

		assertThat(jdbc.queryForList(kept)).isEqualTo(before);
		assertThat(jdbc.queryForList("select pincode from area_pincodes where area_id = 9007199254740102",
			String.class)).containsExactly("411045");
		assertThat(jdbc.queryForList("select name || ':' || status from areas where city_code = 'BLR' and name_key in"
			+ " ('koramangala', 'kormangala', 'whitefield', 'itpl') order by name", String.class))
			.containsExactly("ITPL:ACTIVE", "Koramangala:ACTIVE", "Kormangala:PENDING");
		assertThat(pins(jdbc, "BLR", "koramangala")).containsExactly("560034", "560095");
		assertThat(jdbc.queryForList("select name from areas where city_code = 'PNQ' and name_key = 'baner'",
			String.class)).containsExactly("Baner");

		assertThat(jdbc.queryForList("select id, name, aliases, status, created_by from areas where city_code = 'BLR'"
			+ " and name_key in ('indiranagar', 'jayanagar') order by name_key")).containsExactly(
			row(9007199254740104L, "Indiranagar", "Indira Nagar", "ACTIVE", PROPOSER),
			row(9007199254740105L, "Jayanagar", null, "ACTIVE", null));
		assertThat(pins(jdbc, "BLR", "indiranagar")).containsExactly("560038");
		assertThat(pins(jdbc, "BLR", "jayanagar")).containsExactly("560011", "560041");

		List<String> counts = jdbc.queryForList("select city_code || ':' || count(*) from areas"
			+ " where id between 201 and 381 group by city_code order by city_code", String.class);
		assertThat(counts).containsExactly("BLR:79", "PNQ:80");
		Integer pinsOfSkipped = jdbc.queryForObject("select count(*) from area_pincodes where area_id in (203, 204, 205,"
			+ " 301)", Integer.class);
		assertThat(pinsOfSkipped).isZero();
		assertThat(pins(jdbc, "BLR", "hsr layout")).containsExactly("560102");

		String all = "select a.id, a.name, a.aliases, a.status, a.created_by, a.is_demo, p.pincode from areas a"
			+ " left join area_pincodes p on p.area_id = a.id order by a.id, p.pincode";
		List<Map<String, Object>> migrated = jdbc.queryForList(all);
		new ResourceDatabasePopulator(new ClassPathResource("db/migration/V18__blr_pnq_areas.sql")).execute(dataSource);
		assertThat(jdbc.queryForList(all)).as("V18 run again changes nothing").isEqualTo(migrated);
	}

	private static List<String> pins(JdbcTemplate jdbc, String city, String nameKey) {
		return jdbc.queryForList("select p.pincode from areas a join area_pincodes p on p.area_id = a.id"
			+ " where a.city_code = ? and a.name_key = ? order by p.pincode", String.class, city, nameKey);
	}

	private static Map<String, Object> row(long id, String name, String aliases, String status, Long createdBy) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", id);
		row.put("name", name);
		row.put("aliases", aliases);
		row.put("status", status);
		row.put("created_by", createdBy);
		return row;
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
