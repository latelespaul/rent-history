package com.rentrix.rentrixserver.seed;

import com.rentrix.rentrixserver.admin.ModerationActionRepository;
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
import com.rentrix.rentrixserver.identity.IdHasher;
import com.rentrix.rentrixserver.identity.IdentityVerificationRepository;
import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.rating.RatingRecomputer;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.review.ReviewResponseRepository;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.tenancy.EvidenceLevel;
import com.rentrix.rentrixserver.tenancy.RentRecord;
import com.rentrix.rentrixserver.tenancy.RentRecordRepository;
import com.rentrix.rentrixserver.tenancy.RentRecordStatus;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.VerificationApplicationRepository;
import com.rentrix.rentrixserver.verification.VerificationEventRepository;
import com.rentrix.rentrixserver.verification.VerificationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dev-only demo data (§16, D-68, D-69) so a local run has homes to browse and tenancies to show: 4 published
 * properties in Bangalore and 4 in Pune, their 4 landlords and 4 tenants per city, all {@code is_demo}, emails on
 * the reserved {@code example.test} domain, no phone numbers, no Google ids (sign in through dev login, D-77).
 * <p>Location hierarchy (D-115): an area with real PIN codes for every seed locality, and one gated society per
 * city with 2 towers of 2 wings each (Demo Greens, Koramangala; Demo Meadows, Baner). Demo Residency A is in
 * Demo Greens Tower 1 · Wing A and Demo Towers E in Demo Meadows Tower 2 · Wing B; every other home is
 * standalone (no society).
 * <p>Tenancies (PLAN B5): every home has three tenancies confirmed by both sides, two ended ones (2019–2021 and
 * 2022–2024) and a current one, each with a rent increase a year in, so every rent history shows changes. One
 * request (Demo Tenant PNQ-3 on Demo Towers E) waits for the landlord's confirmation. Every demo party is approved
 * and ID verified (see below), so dev login can run the whole flow.
 * <p>Reviews (PLAN B6, {@link DemoReviews}): every confirmed tenancy has its three reviews (tenant → landlord,
 * landlord → tenant, tenant → home) with ratings spread over 1–5, except the current tenancy of Demo Villa C:
 * there only the landlord's review is in, awaiting reveal, so Demo Tenant BLR-3 has two review tasks. One property
 * review has the landlord's response. Aggregates are rebuilt from these reviews at the end.
 * <p>Runs only with the {@code dev} profile <em>and</em> {@code SEED_DEMO=true}, and only into an empty
 * database (no users, properties or areas); otherwise it does nothing. Verification states (the demo admin,
 * approved demo parties and the admin queue) come from {@link DemoVerificationSeed}. PLAN B10 extends it with
 * the pending location proposal (Demo Enclave).
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
	static final List<String> TENANTS = List.of("BLR-1", "BLR-2", "BLR-3", "BLR-4", "PNQ-1", "PNQ-2", "PNQ-3", "PNQ-4");
	
	/** Current monthly rent (before the demo increase) per home of {@link #HOMES}, same order. */
	static final List<Long> RENTS = List.of(32_000L, 22_000L, 65_000L, 18_000L, 26_000L, 17_000L, 38_000L, 9_000L);
	
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
	/** Demo accounts predate their oldest tenancy, so their reviews read as written by real members. */
	private static final Instant USERS_SINCE = Instant.parse("2018-11-01T00:00:00Z");
	
	private final UserRepository users;
	private final PropertyRepository properties;
	private final CityRepository cities;
	private final AreaRepository areas;
	private final SocietyRepository societies;
	private final SocietyTowerRepository towers;
	private final TenancyRepository tenancies;
	private final RentRecordRepository rentRecords;
	private final DemoVerificationSeed verification;
	private final DemoReviews reviews;
	private final ReviewRepository reviewRows;
	private final RatingRecomputer recomputer;
	private final TransactionTemplate tx;
	
	public DemoSeeder(UserRepository users, PropertyRepository properties, CityRepository cities,
		AreaRepository areas, SocietyRepository societies, SocietyTowerRepository towers,
		TenancyRepository tenancies, RentRecordRepository rentRecords,
		VerificationApplicationRepository applications, VerificationEventRepository events,
		IdentityVerificationRepository identityChecks, ModerationActionRepository audit, IdHasher hasher,
		ReviewRepository reviewRepository, ReviewResponseRepository responses, RatingRecomputer recomputer,
		Clock clock,
		PlatformTransactionManager transactionManager) {
		this.users = users;
		this.properties = properties;
		this.cities = cities;
		this.areas = areas;
		this.societies = societies;
		this.towers = towers;
		this.tenancies = tenancies;
		this.rentRecords = rentRecords;
		this.verification = new DemoVerificationSeed(users, cities, applications, events, identityChecks, audit,
			hasher, SEEDED_AT);
		this.reviews = new DemoReviews(reviewRepository, responses, clock);
		this.reviewRows = reviewRepository;
		this.recomputer = recomputer;
		this.tx = new TransactionTemplate(transactionManager);
	}
	
	@Override
	public void run(ApplicationArguments args) {
		seed();
	}
	
	/** Seeds an empty database; returns false (and changes nothing) when any user, property or demo area exists. */
	public boolean seed() {
		Boolean seeded = tx.execute(status -> {
			// The unclaimed-homes account (D-139) is created at startup and the launch-city areas (V11) by Flyway;
			// neither counts as data.
			if (users.countByEmailNot(UnclaimedLandlord.EMAIL) > 0 || properties.count() > 0 || areas.existsByDemoTrue()) {
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
			List<Property> homes = HOMES.stream().map(home -> home(home, areaByName.get(home.locality()),
				societyByName.get(home.society()), towerByPath.get(home.society() + "/" + home.tower()))).toList();
			List<Tenancy> confirmed = tenancies(homes);
			int seededApplications = verification.seed();
			int seededReviews = reviews.seed(confirmed, homes.get(2));
			greenTicks(homes, confirmed);
			recomputer.recomputeAll();
			log.info("Demo seed: {} landlords, {} tenants, {} areas, {} societies, {} published properties, "
				+ "{} tenancies, {} reviews, {} verification applications.", LANDLORDS.size(), TENANTS.size(),
				AREAS.size(), SOCIETIES.size(), HOMES.size(), confirmed.size() + 1, seededReviews, seededApplications);
			return true;
		});
		return Boolean.TRUE.equals(seeded);
	}
	
	/**
	 * D-134 (design v4, open by default): the demo admin has given the green tick to every published demo home, every
	 * confirmed demo tenancy and every published demo review, so the demo keeps its ratings in either headline mode
	 * (D-135). The pending tenancy and the review awaiting reveal stay unverified.
	 */
	private void greenTicks(List<Property> homes, List<Tenancy> confirmed) {
		User admin = users.findByEmail(DemoVerificationSeed.ADMIN_EMAIL).orElseThrow();
		Instant decidedAt = SEEDED_AT.plusSeconds(86_400L);
		homes.forEach(home -> home.setVerificationStatus(VerificationState.VERIFIED));
		properties.saveAll(homes);
		for (Tenancy tenancy : confirmed) {
			tenancy.setVerificationStatus(VerificationState.VERIFIED);
			tenancy.setVerificationDecidedBy(admin);
			tenancy.setVerificationDecidedAt(decidedAt);
		}
		tenancies.saveAll(confirmed);
		List<Review> published = reviewRows.findAll().stream()
			.filter(review -> review.getStatus() == ReviewStatus.PUBLISHED)
			.toList();
		for (Review review : published) {
			review.setVerificationStatus(VerificationState.VERIFIED);
			review.setVerificationDecidedBy(admin);
			review.setVerificationDecidedAt(decidedAt);
		}
		reviewRows.saveAll(published);
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
		user.setCreatedAt(USERS_SINCE);
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
	
	private Property home(DemoHome home, Area area, Society society, SocietyTower tower) {
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
		return properties.save(property);
	}
	
	/**
	 * Home {@code k} (0–3) of a city, oldest first: an ended tenancy of tenant {@code (k+2) mod 4 + 1}, 24 months
	 * from 2019-(1+k)-01; an ended tenancy of tenant {@code k+2} (even {@code k}) or {@code k} (odd {@code k}),
	 * 20 months from 2022-(6+k)-01, ending before that tenant's own current tenancy; and a current tenancy of tenant
	 * {@code k+1} from 2024-(3+k)-01. Each has a 5% (ended) or 7% (current) increase twelve months in.
	 *
	 * @return the confirmed tenancies, in that order per home
	 */
	private List<Tenancy> tenancies(List<Property> homes) {
		List<Tenancy> confirmed = new ArrayList<>();
		for (int i = 0; i < homes.size(); i++) {
			Property home = homes.get(i);
			String city = home.getCity().getCode();
			int k = i % 4;
			long rent = RENTS.get(i);
			LocalDate earlyStart = LocalDate.of(2019, 1 + k, 1);
			Tenancy early = tenancy(home, tenant(city, (k + 2) % 4 + 1), earlyStart,
				earlyStart.plusMonths(24).minusDays(1), round(rent * 0.8), TenancyParty.TENANT);
			rent(early, earlyStart.plusYears(1), round(early.getInitialRentInr() * 1.05), home.getLandlord());
			confirmed.add(early);
			LocalDate pastStart = LocalDate.of(2022, 6 + k, 1);
			Tenancy past = tenancy(home, tenant(city, k % 2 == 0 ? k + 2 : k), pastStart,
				pastStart.plusMonths(20).minusDays(1), round(rent * 0.88), TenancyParty.TENANT);
			rent(past, pastStart.plusYears(1), round(past.getInitialRentInr() * 1.05), home.getLandlord());
			confirmed.add(past);
			LocalDate start = LocalDate.of(2024, 3 + k, 1);
			Tenancy current = tenancy(home, tenant(city, k + 1), start, null, rent,
				k % 2 == 0 ? TenancyParty.LANDLORD : TenancyParty.TENANT);
			rent(current, start.plusYears(1), round(rent * 1.07), home.getLandlord());
			confirmed.add(current);
		}
		Tenancy pending = new Tenancy();
		Property towers = homes.get(4);
		pending.setProperty(towers);
		pending.setLandlord(towers.getLandlord());
		pending.setTenant(tenant("PNQ", 3));
		pending.setStartDate(LocalDate.of(2021, 6, 1));
		pending.setEndDate(LocalDate.of(2022, 5, 31));
		pending.setInitialRentInr(21_000L);
		pending.setDepositInr(63_000L);
		pending.setInitiatedBy(TenancyParty.TENANT);
		pending.setStatus(TenancyStatus.PENDING_CONFIRMATION);
		tenancies.save(pending);
		return confirmed;
	}
	
	private Tenancy tenancy(Property home, User tenant, LocalDate start, LocalDate end, long rent,
		TenancyParty initiatedBy) {
		Instant created = start.atStartOfDay().toInstant(ZoneOffset.UTC);
		Tenancy tenancy = new Tenancy();
		tenancy.setProperty(home);
		tenancy.setLandlord(home.getLandlord());
		tenancy.setTenant(tenant);
		tenancy.setStartDate(start);
		tenancy.setEndDate(end);
		tenancy.setInitialRentInr(rent);
		tenancy.setDepositInr(rent * 3);
		tenancy.setInitiatedBy(initiatedBy);
		tenancy.setStatus(end == null ? TenancyStatus.ACTIVE : TenancyStatus.ENDED);
		tenancy.setEvidenceLevel(EvidenceLevel.MUTUAL);
		tenancy.setConfirmedAt(created.plusSeconds(3 * 86_400L));
		if (end != null) {
			tenancy.setDepositRefundedInr(rent * 3);
			tenancy.setEndedAt(end.atStartOfDay().toInstant(ZoneOffset.UTC));
		}
		tenancy.setCreatedAt(created);
		Tenancy saved = tenancies.save(tenancy);
		rent(saved, start.withDayOfMonth(1), rent, initiatedBy == TenancyParty.LANDLORD ? home.getLandlord() : tenant);
		return saved;
	}
	
	private void rent(Tenancy tenancy, LocalDate from, long monthlyRent, User addedBy) {
		RentRecord record = new RentRecord();
		record.setTenancy(tenancy);
		record.setEffectiveFrom(from.withDayOfMonth(1));
		record.setMonthlyRentInr(monthlyRent);
		record.setAddedBy(addedBy);
		record.setStatus(RentRecordStatus.CONFIRMED);
		record.setCreatedAt(from.atStartOfDay().toInstant(ZoneOffset.UTC));
		rentRecords.save(record);
	}
	
	private User tenant(String city, int number) {
		return users.findByEmail("demo-tenant-" + city.toLowerCase(Locale.ROOT) + "-" + number + "@example.test")
			.orElseThrow();
	}
	
	/** Rents end in 00 or 500 like real ones. */
	private static long round(double rupees) {
		return Math.round(rupees / 500) * 500;
	}
	
}
