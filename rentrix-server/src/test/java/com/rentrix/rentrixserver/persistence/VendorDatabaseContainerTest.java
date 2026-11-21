package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.RentrixServerApplication;
import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Starts the application on real MySQL 8 and PostgreSQL 16: Flyway migrates, Hibernate
 * validates every entity, and a user round-trips. Skipped when Docker is not available
 * (CI runners have it).
 */
@Testcontainers(disabledWithoutDocker = true)
class VendorDatabaseContainerTest {
	
	@Test
	void mysql() {
		try (MySQLContainer db = new MySQLContainer("mysql:8.4")) {
			db.start();
			assertAppRunsOn(db);
		}
	}
	
	@Test
	void postgres() {
		try (PostgreSQLContainer db = new PostgreSQLContainer("postgres:16-alpine")) {
			db.start();
			assertAppRunsOn(db);
		}
	}
	
	private static void assertAppRunsOn(JdbcDatabaseContainer<?> db) {
		try (ConfigurableApplicationContext app = new SpringApplicationBuilder(RentrixServerApplication.class)
			.profiles("test")
			// the same env names a deployment uses (D-09); a random port, as the app is a servlet app
			.properties("server.port=0", "DB_URL=" + db.getJdbcUrl(), "DB_USERNAME=" + db.getUsername(),
				"DB_PASSWORD=" + db.getPassword())
			.run()) {
			assertThat(app.getBean(CityRepository.class).findByLiveTrueOrderByNameAsc()).hasSize(2);
			UserRepository users = app.getBean(UserRepository.class);
			User user = new User();
			user.setDisplayName("Demo Vendor");
			user.setEmail(UUID.randomUUID() + "@example.test");
			user.setRoles(EnumSet.of(Role.TENANT));
			User saved = users.save(user);
			User loaded = users.findByPublicId(saved.getPublicId()).orElseThrow();
			assertThat(loaded.getRoles()).containsExactly(Role.TENANT);
			assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
		}
	}
	
}
