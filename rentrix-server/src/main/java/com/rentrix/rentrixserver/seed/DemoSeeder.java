package com.rentrix.rentrixserver.seed;

import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Minimal dev-only demo data (§16, D-68, D-69) so a local run has homes to browse: 4 published properties in
 * Bangalore and 4 in Pune, their 4 landlords and one tenant per city, all {@code is_demo}, emails on the
 * reserved {@code example.test} domain, no phone numbers, no Google ids (sign in through dev login, D-77).
 * <p>Runs only with the {@code dev} profile <em>and</em> {@code SEED_DEMO=true}, and only into an empty
 * database (no users and no properties); otherwise it does nothing. PLAN B10 extends it with tenancies, rent
 * history, reviews, verification states and the location hierarchy.
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {
	
	/** Demo homes of the §16 seed plan: real locality names and PIN codes, fictional buildings. */
	record DemoHome(String city, String landlord, String building, String locality, String pincode,
		PropertyType type, Integer bedrooms, int bathrooms, int areaSqft, Furnishing furnishing, String unit) {
	}
	
	static final List<String> LANDLORDS = List.of("BLR-1", "BLR-2", "PNQ-1", "PNQ-2");
	static final List<String> TENANTS = List.of("BLR-1", "PNQ-1");
	
	static final List<DemoHome> HOMES = List.of(
		new DemoHome("BLR", "BLR-1", "Demo Residency A", "Koramangala", "560034", PropertyType.APARTMENT, 2, 2, 1150,
			Furnishing.SEMI, "D-101"),
		new DemoHome("BLR", "BLR-1", "Demo Heights B", "HSR Layout", "560102", PropertyType.APARTMENT, 1, 1, 650,
			Furnishing.FULL, "B-204"),
		new DemoHome("BLR", "BLR-2", "Demo Villa C", "Whitefield", "560066", PropertyType.VILLA, 3, 3, 2400,
			Furnishing.SEMI, "Villa 7"),
		new DemoHome("BLR", "BLR-2", "Demo Studio D", "Indiranagar", "560038", PropertyType.STUDIO, 0, 1, 420,
			Furnishing.FULL, "S-3"),
		new DemoHome("PNQ", "PNQ-1", "Demo Towers E", "Baner", "411045", PropertyType.APARTMENT, 2, 2, 1050,
			Furnishing.SEMI, "E-1203"),
		new DemoHome("PNQ", "PNQ-1", "Demo Park F", "Hinjewadi", "411057", PropertyType.APARTMENT, 1, 1, 600,
			Furnishing.UNFURNISHED, "F-502"),
		new DemoHome("PNQ", "PNQ-2", "Demo Court G", "Kothrud", "411038", PropertyType.INDEPENDENT_HOUSE, 3, 2, 1800,
			Furnishing.UNFURNISHED, "House 12"),
		new DemoHome("PNQ", "PNQ-2", "Demo Nest H", "Viman Nagar", "411014", PropertyType.PG, 1, 1, 180,
			Furnishing.FULL, "Room 2"));
	
	private static final Instant SEEDED_AT = Instant.parse("2025-01-15T00:00:00Z");
	
	private final UserRepository users;
	private final PropertyRepository properties;
	private final CityRepository cities;
	private final TransactionTemplate tx;
	
	public DemoSeeder(UserRepository users, PropertyRepository properties, CityRepository cities,
		PlatformTransactionManager transactionManager) {
		this.users = users;
		this.properties = properties;
		this.cities = cities;
		this.tx = new TransactionTemplate(transactionManager);
	}
	
	@Override
	public void run(ApplicationArguments args) {
		seed();
	}
	
	/** Seeds an empty database; returns false (and changes nothing) when any user or property exists. */
	public boolean seed() {
		Boolean seeded = tx.execute(status -> {
			if (users.count() > 0 || properties.count() > 0) {
				log.info("Demo seed skipped: the database is not empty.");
				return false;
			}
			for (String code : LANDLORDS) {
				user("Demo Landlord " + code, "demo-landlord-" + code, Role.LANDLORD, code);
			}
			for (String code : TENANTS) {
				user("Demo Tenant " + code, "demo-tenant-" + code, Role.TENANT, code);
			}
			for (DemoHome home : HOMES) {
				home(home);
			}
			log.info("Demo seed: {} landlords, {} tenants, {} published properties.", LANDLORDS.size(),
				TENANTS.size(), HOMES.size());
			return true;
		});
		return Boolean.TRUE.equals(seeded);
	}
	
	private void user(String name, String emailLocalPart, Role role, String code) {
		User user = new User();
		user.setDisplayName(name);
		user.setEmail(emailLocalPart.toLowerCase(Locale.ROOT) + "@example.test");
		user.setEmailVerifiedAt(SEEDED_AT);
		user.setOnboardedAt(SEEDED_AT);
		user.setRoles(EnumSet.of(role));
		user.setHomeCity(cities.getReferenceById(code.substring(0, 3)));
		user.setDemo(true);
		user.setCreatedAt(SEEDED_AT);
		users.save(user);
	}
	
	private void home(DemoHome home) {
		User landlord = users.findByEmail(("demo-landlord-" + home.landlord() + "@example.test").toLowerCase(Locale.ROOT))
			.orElseThrow();
		Property property = new Property();
		property.setLandlord(landlord);
		property.setCity(cities.getReferenceById(home.city()));
		property.setLocality(home.locality());
		property.setBuildingName(home.building());
		property.setStreet("Demo Road");
		property.setUnitLabel(home.unit());
		property.setPincode(home.pincode());
		property.setPropertyType(home.type());
		property.setBedrooms(home.bedrooms());
		property.setBathrooms(home.bathrooms());
		property.setAreaSqft(home.areaSqft());
		property.setFurnishing(home.furnishing());
		property.setParking(home.type() != PropertyType.PG && home.type() != PropertyType.STUDIO);
		property.setDescription("Demo listing for local development. Not a real home.");
		property.setStatus(PropertyStatus.PUBLISHED);
		property.setDemo(true);
		property.setCreatedAt(SEEDED_AT.plusSeconds(60L * HOMES.indexOf(home)));
		properties.save(property);
	}
	
}
