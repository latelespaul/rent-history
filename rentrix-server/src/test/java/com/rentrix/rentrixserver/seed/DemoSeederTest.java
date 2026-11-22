package com.rentrix.rentrixserver.seed;

import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev demo seed (D-68, D-69) on its own empty database: 4 + 4 published demo homes with demo landlords,
 * no phones, {@code example.test} emails; it never runs twice and its bean does not exist outside {@code dev}.
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:sqlite:target/test-data/seed-${random.uuid}.db",
	"app.seed.demo=true"})
@ActiveProfiles("test")
class DemoSeederTest {
	
	@Autowired
	UserRepository users;
	
	@Autowired
	PropertyRepository properties;
	
	@Autowired
	CityRepository cities;
	
	@Autowired
	PlatformTransactionManager transactionManager;
	
	@Autowired
	ApplicationContext context;
	
	@Test
	void seedsAnEmptyDatabaseOnceWithClearlyFakeData() {
		assertThat(context.getBeansOfType(DemoSeeder.class)).as("dev-only bean").isEmpty();
		DemoSeeder seeder = new DemoSeeder(users, properties, cities, transactionManager);
		assertThat(seeder.seed()).isTrue();
		assertThat(seeder.seed()).as("non-empty database").isFalse();
		
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			List<Property> homes = properties.findAll();
			assertThat(homes).hasSize(8).allMatch(Property::isDemo)
				.allMatch(p -> p.getStatus() == PropertyStatus.PUBLISHED);
			Map<String, Long> perCity = homes.stream()
				.collect(Collectors.groupingBy(p -> p.getCity().getCode(), Collectors.counting()));
			assertThat(perCity).containsExactlyInAnyOrderEntriesOf(Map.of("BLR", 4L, "PNQ", 4L));
			assertThat(homes).allMatch(p -> p.getBuildingName().startsWith("Demo "));
			assertThat(homes.stream().map(p -> p.getLandlord().getDisplayName()).distinct())
				.containsExactlyInAnyOrder("Demo Landlord BLR-1", "Demo Landlord BLR-2", "Demo Landlord PNQ-1",
					"Demo Landlord PNQ-2");
			assertThat(homes).allMatch(p -> p.getPincode().startsWith(p.getCity().getCode().equals("BLR") ? "56" : "41"));
			List<User> people = users.findAll();
			assertThat(people).hasSize(6).allMatch(User::isDemo)
				.allMatch(u -> u.getEmail().endsWith("@example.test"))
				.allMatch(u -> u.getPhone() == null && u.getGoogleSub() == null)
				.allMatch(u -> u.getEmailVerifiedAt() != null && u.getOnboardedAt() != null);
		});
	}
	
}
