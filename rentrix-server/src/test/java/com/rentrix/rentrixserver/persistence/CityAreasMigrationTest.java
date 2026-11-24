package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.config.MigrationPlaceholders;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V18 (captain 2026-11-24) on a database that already has BLR and PNQ areas, as LIVE (one user's PENDING
 * proposal) and dev (the demo seed's rows) do: a city's existing place is never added twice, under its own name,
 * one of the new row's aliases, or an alias of the existing row, and existing rows stay as they are.
 */
class CityAreasMigrationTest {

	private static final String ROW = "insert into areas (id, public_id, city_code, name, name_key, aliases, status,"
		+ " created_by, is_demo, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, null, ?,"
		+ " '2026-11-20 00:00:00.000', '2026-11-20 00:00:00.000')";

	private static final String PIN = "insert into area_pincodes (area_id, pincode, created_at, updated_at)"
		+ " values (?, ?, '2026-11-20 00:00:00.000', '2026-11-20 00:00:00.000')";

	@Test
	void skipsPlacesTheCityAlreadyHasAndKeepsThoseRows(@TempDir Path dir) {
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:" + dir.resolve("v18.db"));
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		flyway(dataSource, "17").migrate();
		// a user's PENDING proposal named like an alias of Koramangala
		jdbc.update(ROW, 9007199254740101L, "00000000-0000-7000-8000-000000000101", "BLR", "Kormangala",
			"kormangala", null, "PENDING", false);
		// demo rows: Baner by its own name, Whitefield through the alias of another row
		jdbc.update(ROW, 9007199254740102L, "00000000-0000-7000-8000-000000000102", "PNQ", "Baner", "baner", null,
			"ACTIVE", true);
		jdbc.update(PIN, 9007199254740102L, "411045");
		jdbc.update(ROW, 9007199254740103L, "00000000-0000-7000-8000-000000000103", "BLR", "ITPL", "itpl",
			"Whitefield", "ACTIVE", true);
		String existing = "select * from areas where id > 9007199254740000 order by id";
		List<Map<String, Object>> before = jdbc.queryForList(existing);

		assertThat(flyway(dataSource, "latest").migrate().success).isTrue();

		assertThat(jdbc.queryForList(existing)).isEqualTo(before);
		assertThat(jdbc.queryForList("select pincode from area_pincodes where area_id = 9007199254740102",
			String.class)).containsExactly("411045");
		assertThat(jdbc.queryForList("select name from areas where city_code = 'BLR' and name_key in"
			+ " ('koramangala', 'kormangala', 'whitefield', 'itpl') order by name", String.class))
			.containsExactly("ITPL", "Kormangala");
		assertThat(jdbc.queryForList("select name from areas where city_code = 'PNQ' and name_key = 'baner'",
			String.class)).containsExactly("Baner");
		List<String> counts = jdbc.queryForList("select city_code || ':' || count(*) from areas"
			+ " where id between 201 and 381 group by city_code order by city_code", String.class);
		assertThat(counts).containsExactly("BLR:80", "PNQ:80");
		Integer pinsOfSkipped = jdbc.queryForObject("select count(*) from area_pincodes where area_id in (201, 203, 301)",
			Integer.class);
		assertThat(pinsOfSkipped).isZero();
		assertThat(jdbc.queryForList("select p.pincode from areas a join area_pincodes p on p.area_id = a.id"
			+ " where a.city_code = 'BLR' and a.name_key = 'hsr layout'", String.class)).containsExactly("560102");
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
