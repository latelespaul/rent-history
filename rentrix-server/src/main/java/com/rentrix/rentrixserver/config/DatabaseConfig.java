package com.rentrix.rentrixserver.config;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

/**
 * One database setting, {@code DB_URL}, picks SQLite, MySQL or PostgreSQL (D-09). This class
 * applies the SQLite connection settings and gives Flyway the vendor placeholders. SQLite
 * errors are mapped by {@link RentrixSqliteDialect} (JPA) and {@code sql-error-codes.xml}
 * (JdbcTemplate), so a constraint failure is a {@code DataIntegrityViolationException} on
 * every vendor.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {
	
	@Bean
	static BeanPostProcessor sqliteDataSourceSettings() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) {
				if (bean instanceof HikariDataSource hikari && SqliteSupport.isSqlite(hikari.getJdbcUrl())) {
					SqliteSupport.configure(hikari);
				}
				return bean;
			}
		};
	}
	
	/** On SQLite, use {@link RentrixSqliteDialect}; other databases keep Hibernate's detection. */
	@Bean
	HibernatePropertiesCustomizer sqliteDialect(DataSource dataSource) {
		return properties -> {
			if (dataSource instanceof HikariDataSource hikari && SqliteSupport.isSqlite(hikari.getJdbcUrl())) {
				properties.putIfAbsent(AvailableSettings.DIALECT, RentrixSqliteDialect.class.getName());
			}
		};
	}
	
	@Bean
	FlywayConfigurationCustomizer vendorMigrationPlaceholders() {
		return configuration -> {
			Map<String, String> placeholders = new HashMap<>(configuration.getPlaceholders());
			placeholders.putAll(MigrationPlaceholders.forDataSource(configuration.getDataSource()));
			configuration.placeholders(placeholders);
		};
	}
	
}
