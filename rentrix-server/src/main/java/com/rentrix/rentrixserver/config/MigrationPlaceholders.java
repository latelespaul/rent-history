package com.rentrix.rentrixserver.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Values for the Flyway placeholders used by {@code db/migration} (D-10). The one type that
 * differs by vendor is the timestamp column: MySQL's {@code TIMESTAMP} ends in 2038 and is
 * converted through the session time zone, so MySQL gets {@code DATETIME(6)}.
 */
public final class MigrationPlaceholders {
	
	public static final String TIMESTAMP_TYPE = "timestamp_type";
	
	private MigrationPlaceholders() {
	}
	
	/** Placeholders for a database product name as reported by JDBC metadata. */
	public static Map<String, String> forProduct(String databaseProductName) {
		String product = databaseProductName == null ? "" : databaseProductName.toLowerCase(Locale.ROOT);
		boolean mysql = product.contains("mysql") || product.contains("mariadb");
		return Map.of(TIMESTAMP_TYPE, mysql ? "DATETIME(6)" : "TIMESTAMP");
	}
	
	public static Map<String, String> forDataSource(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection()) {
			return forProduct(connection.getMetaData().getDatabaseProductName());
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read database metadata for Flyway placeholders", e);
		}
	}
	
}
