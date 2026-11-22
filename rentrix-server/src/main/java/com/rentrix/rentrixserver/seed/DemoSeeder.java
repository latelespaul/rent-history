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
import com.rentrix.rentrixserver.location.TowerKind;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal dev-only demo data (§16, D-68, D-69) so a local run has homes to browse: 4 published properties in
 * Bangalore and 4 in Pune, their 4 landlords and one tenant per city, all {@code is_demo}, emails on the
 * reserved {@code example.test} domain, no phone numbers, no Google ids (sign in through dev login, D-77).
 * <p>Location hierarchy (D-115): an area with real PIN codes for every seed locality, and one gated society per
 * city with 2 towers of 2 wings each (Demo Greens, Koramangala; Demo Meadows, Baner). Demo Residency A is in
 * Demo Greens Tower 1 · Wing A and Demo Towers E in Demo Meadows Tower 2 · Wing B; every other home is
 * standalone (no society).
 * <p>Runs only with the {@code dev} profile <em>and</em> {@code SEED_DEMO=true}, and only into an empty
 * database (no users, properties or areas); otherwise it does nothing. PLAN B10 extends it with tenancies, rent
 * history, reviews, verification states and the pending location proposal (Demo Enclave).
 */
@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.seed", name = "demo", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {
	
	/** Demo homes of the §16 seed plan: real locality names and PIN codes, fictional buildings. */
	record DemoHome(String city, String landlord, String building, String locality, String pincode,
		PropertyType type, Integer bedrooms, int bathrooms, int areaSqft, Furnishing furnishing, String unit,
		String society, String tower, String wing) {
		
		DemoHome(String city, String landlord, String building, String locality, String pincode, PropertyType type,
			Integer bedrooms, int bathrooms, int areaSqft, Furnishing furnishing, String unit) {
			this(city, landlord, building, locality, pincode, type, bedrooms, bathrooms, areaSqft, furnishing, unit,
				null, null, null);
		}
		
	}
	
	/** A seed area: real locality name and PIN codes (D-69, D-115). */
	record DemoArea(String city, String name, String aliases, List<String> pincodes) {
	}
	
	/** A seed gated society; each gets {@link #TOWERS} with {@link #WINGS}. */
	record DemoSociety(String city, String area, String name, String developer) {
	}
	
	static final List<DemoArea> AREAS = List.of(
		new DemoArea("BLR", "Koramangala", "Kormangala", List.of("560034", "560095")),
		new DemoArea("BLR", "HSR Layout", "HSR", List.of("560102")),
		new DemoArea("BLR", "Whitefield", null, List.of("560066")),
		new DemoArea("BLR", "Indiranagar", "Indira Nagar", List.of("560038")),
		new DemoArea("BLR", "Jayanagar", null, List.of("560011", "560041")),
		new DemoArea("PNQ", "Baner", null, List.of("411045")),
		new DemoArea("PNQ", "Hinjewadi", "Hinjawadi", List.of("411057")),
		new DemoArea("PNQ", "Kothrud", null, List.of("411038")),
		new DemoArea("PNQ", "Viman Nagar", "Vimannagar", List.of("411014")));
	
	static final List<DemoSociety> SOCIETIES = List.of(
		new DemoSociety("BLR", "Koramangala", "Demo Greens", "Demo Builders"),
		new DemoSociety("PNQ", "Baner", "Demo Meadows", null));
	
	static final List<String> TOWERS = List.of("Tower 1", "Tower 2");
	static final List<String> WINGS = List.of("Wing A", "Wing B");
	
	static final List<String> LANDLORDS = List.of("BLR-1", "BLR-2", "PNQ-1", "PNQ-2");
	static final List<String> TENANTS = List.of("BLR-1", "PNQ-1");
	
	static final List<DemoHome> HOMES = List.of(
		new DemoHome("BLR", "BLR-1", "Demo Residency A", "Koramangala", "560034", PropertyType.APARTMENT, 2, 2, 1150,
			Furnishing.SEMI, "D-101", "Demo Greens", "Tower 1", "Wing A"),
		new DemoHome("BLR", "BLR-1", "Demo Heights B", "HSR Layout", "560102", PropertyType.APARTMENT, 1, 1, 650,
			Furnishing.FULL, "B-204"),
		new DemoHome("BLR", "BLR-2", "Demo Villa C", "Whitefield", "560066", PropertyType.VILLA, 3, 3, 2400,
			Furnishing.SEMI, "Villa 7"),
		new DemoHome("BLR", "BLR-2", "Demo Studio D", "Indiranagar", "560038", PropertyType.STUDIO, 0, 1, 420,
			Furnishing.FULL, "S-3"),
		new DemoHome("PNQ", "PNQ-1", "Demo Towers E", "Baner", "411045", PropertyType.APARTMENT, 2, 2, 1050,
			Furnishing.SEMI, "E-1203", "Demo Meadows", "Tower 2", "Wing B"),
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
	private final AreaRepository areas;
	private final SocietyRepository societies;
	private final SocietyTowerRepository towers;
	private final TransactionTemplate tx;
	
	public DemoSeeder(UserRepository users, PropertyRepository properties, CityRepository cities,
		AreaRepository areas, SocietyRepository societies, SocietyTowerRepository towers,
		PlatformTransactionManager transactionManager) {
		this.users = users;
		this.properties = properties;
		this.cities = cities;
		this.areas = areas;
		this.societies = societies;
		this.towers = towers;
		this.tx = new TransactionTemplate(transactionManager);
	}
	
	@Override
	public void run(ApplicationArguments args) {
		seed();
	}
	
	/** Seeds an empty database; returns false (and changes nothing) when any user, property or area exists. */
	public boolean seed() {
		Boolean seeded = tx.execute(status -> {
			if (users.count() > 0 || properties.count() > 0 || areas.count() > 0) {
				log.info("Demo seed skipped: the database is not empty.");
				return false;
			}
			for (String code : LANDLORDS) {
				user("Demo Landlord " + code, "demo-landlord-" + code, Role.LANDLORD, code);
			}
			for (String code : TENANTS) {
				user("Demo Tenant " + code, "demo-tenant-" + code, Role.TENANT, code);
			}
			Map<String, Area> areaByName = new HashMap<>();
			for (DemoArea area : AREAS) {
				areaByName.put(area.name(), area(area));
			}
			Map<String, SocietyTower> towerByPath = new HashMap<>();
			Map<String, Society> societyByName = new HashMap<>();
			for (DemoSociety society : SOCIETIES) {
				Society saved = society(society, areaByName.get(society.area()));
				societyByName.put(society.name(), saved);
				for (int i = 0; i < TOWERS.size(); i++) {
					towerByPath.put(society.name() + "/" + TOWERS.get(i), tower(saved, TOWERS.get(i), i));
				}
			}
			for (DemoHome home : HOMES) {
				home(home, areaByName.get(home.locality()), societyByName.get(home.society()),
					towerByPath.get(home.society() + "/" + home.tower()));
			}
			log.info("Demo seed: {} landlords, {} tenants, {} areas, {} societies, {} published properties.",
				LANDLORDS.size(), TENANTS.size(), AREAS.size(), SOCIETIES.size(), HOMES.size());
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
	
	private Area area(DemoArea seed) {
		Area area = new Area();
		area.setCity(cities.getReferenceById(seed.city()));
		area.setName(seed.name());
		area.setAliases(seed.aliases());
		seed.pincodes().forEach(area::addPincode);
		area.setStatus(LocationStatus.ACTIVE);
		area.setDemo(true);
		area.setCreatedAt(SEEDED_AT);
		return areas.save(area);
	}
	
	private Society society(DemoSociety seed, Area area) {
		Society society = new Society();
		society.setArea(area);
		society.setName(seed.name());
		society.setDeveloper(seed.developer());
		society.setKind(SocietyKind.GATED_COMMUNITY);
		society.setStreet("Demo Road");
		society.setStatus(LocationStatus.ACTIVE);
		society.setDemo(true);
		society.setCreatedAt(SEEDED_AT);
		return societies.save(society);
	}
	
	private SocietyTower tower(Society society, String name, int order) {
		SocietyTower tower = new SocietyTower();
		tower.setSociety(society);
		tower.setName(name);
		tower.setKind(TowerKind.TOWER);
		tower.setSortOrder(order);
		tower.setStatus(LocationStatus.ACTIVE);
		tower.setCreatedAt(SEEDED_AT);
		for (String wing : WINGS) {
			tower.addWing(wing, LocationStatus.ACTIVE, null).setCreatedAt(SEEDED_AT);
		}
		return towers.save(tower);
	}
	
	private void home(DemoHome home, Area area, Society society, SocietyTower tower) {
		User landlord = users.findByEmail(("demo-landlord-" + home.landlord() + "@example.test").toLowerCase(Locale.ROOT))
			.orElseThrow();
		Property property = new Property();
		property.setLandlord(landlord);
		property.setCity(cities.getReferenceById(home.city()));
		property.setArea(area);
		property.setLocality(area.getName());
		if (society != null) {
			property.setSociety(society);
			property.setTower(tower);
			SocietyWing wing = tower.getWings().stream().filter(w -> w.getName().equals(home.wing())).findFirst()
				.orElseThrow();
			property.setWing(wing);
		}
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
