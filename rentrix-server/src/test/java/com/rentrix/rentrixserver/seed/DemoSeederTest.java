package com.rentrix.rentrixserver.seed;

import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.AreaRepository;
import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyKind;
import com.rentrix.rentrixserver.location.SocietyRepository;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.location.SocietyTowerRepository;
import com.rentrix.rentrixserver.location.SocietyWing;
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
	AreaRepository areas;
	
	@Autowired
	SocietyRepository societies;
	
	@Autowired
	SocietyTowerRepository towers;
	
	@Autowired
	PlatformTransactionManager transactionManager;
	
	@Autowired
	ApplicationContext context;
	
	@Test
	void seedsAnEmptyDatabaseOnceWithClearlyFakeData() {
		assertThat(context.getBeansOfType(DemoSeeder.class)).as("dev-only bean").isEmpty();
		DemoSeeder seeder = new DemoSeeder(users, properties, cities, areas, societies, towers, transactionManager);
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
			// D-115: every home points at its area, whose PIN codes include the home's; the society homes match
			assertThat(homes).allMatch(p -> p.getArea() != null && p.getArea().isDemo()
				&& p.getArea().getCity().getCode().equals(p.getCity().getCode())
				&& p.getArea().pincodeList().contains(p.getPincode())
				&& p.getArea().getName().equals(p.getLocality()));
			Map<String, String> placed = homes.stream().filter(p -> p.getSociety() != null)
				.collect(Collectors.toMap(Property::getBuildingName, p -> p.getSociety().getName() + " / "
					+ p.getTower().getName() + " / " + p.getWing().getName()));
			assertThat(placed).containsExactlyInAnyOrderEntriesOf(Map.of(
				"Demo Residency A", "Demo Greens / Tower 1 / Wing A",
				"Demo Towers E", "Demo Meadows / Tower 2 / Wing B"));
			assertThat(homes).filteredOn(p -> p.getSociety() == null).hasSize(6)
				.allMatch(p -> p.getTower() == null && p.getWing() == null);
			
			List<Society> gated = societies.findAll();
			assertThat(gated).hasSize(2).allMatch(Society::isDemo)
				.allMatch(s -> s.getKind() == SocietyKind.GATED_COMMUNITY && s.getStatus() == LocationStatus.ACTIVE)
				.allMatch(s -> s.getCity().getCode().equals(s.getArea().getCity().getCode()));
			assertThat(gated.stream().map(s -> s.getCity().getCode())).containsExactlyInAnyOrder("BLR", "PNQ");
			for (Society society : gated) {
				List<SocietyTower> its = towers.findBySocietyOrderBySortOrderAscNameKeyAsc(society);
				assertThat(its).extracting(SocietyTower::getName).containsExactly("Tower 1", "Tower 2");
				assertThat(its).allSatisfy(t -> assertThat(t.getWings()).extracting(SocietyWing::getName)
					.containsExactly("Wing A", "Wing B"));
			}
			List<Area> seededAreas = areas.findAll();
			assertThat(seededAreas).hasSize(9).allMatch(Area::isDemo)
				.allMatch(a -> a.getStatus() == LocationStatus.ACTIVE && !a.pincodeList().isEmpty())
				.allMatch(a -> a.pincodeList().stream()
					.allMatch(pin -> pin.startsWith(a.getCity().getCode().equals("BLR") ? "56" : "41")));
			
			List<User> people = users.findAll();
			assertThat(people).hasSize(6).allMatch(User::isDemo)
				.allMatch(u -> u.getEmail().endsWith("@example.test"))
				.allMatch(u -> u.getPhone() == null && u.getGoogleSub() == null)
				.allMatch(u -> u.getEmailVerifiedAt() != null && u.getOnboardedAt() != null);
		});
	}
	
}
