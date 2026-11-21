package com.rentrix.rentrixserver.config;

import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * SQLite connection settings (D-11): WAL journal, foreign keys on, 5 s busy timeout, and
 * timestamps stored as UTC text so rows written by SQL migrations and by the API compare
 * the same way. Also creates the database file's folder, which the driver will not do.
 */
final class SqliteSupport {
	
	static final String URL_PREFIX = "jdbc:sqlite:";
	
	private SqliteSupport() {
	}
	
	static boolean isSqlite(String jdbcUrl) {
		return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith(URL_PREFIX);
	}
	
	static void configure(HikariDataSource dataSource) {
		dataSource.addDataSourceProperty("journal_mode", "WAL");
		dataSource.addDataSourceProperty("foreign_keys", "true");
		dataSource.addDataSourceProperty("busy_timeout", "5000");
		dataSource.addDataSourceProperty("date_class", "TEXT");
		databaseFile(dataSource.getJdbcUrl()).map(Path::getParent).ifPresent(SqliteSupport::createFolder);
	}
	
	/** The file behind a SQLite URL, or empty for in-memory databases. */
	static Optional<Path> databaseFile(String jdbcUrl) {
		String path = jdbcUrl.substring(URL_PREFIX.length());
		int query = path.indexOf('?');
		boolean memory = path.isEmpty() || path.startsWith(":memory:")
			|| (query >= 0 && path.substring(query).contains("mode=memory"));
		if (memory) {
			return Optional.empty();
		}
		if (query >= 0) {
			path = path.substring(0, query);
		}
		if (path.startsWith("file:")) {
			path = path.substring("file:".length());
		}
		return Optional.of(Path.of(path).toAbsolutePath());
	}
	
	private static void createFolder(Path folder) {
		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot create the SQLite database folder " + folder, e);
		}
	}
	
}
