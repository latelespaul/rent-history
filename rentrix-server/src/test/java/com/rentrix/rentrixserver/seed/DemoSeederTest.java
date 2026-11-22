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
import com.rentrix.rentrixserver.identity.IdHasher;
import com.rentrix.rentrixserver.identity.IdentityMethod;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.identity.IdentityStatus;
import com.rentrix.rentrixserver.identity.IdentityVerification;
import com.rentrix.rentrixserver.identity.IdentityVerificationRepository;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.rating.RatingRecomputer;
import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.RatingAggregateId;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewContentRules;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.review.ReviewResponseRepository;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.tenancy.RentRecord;
import com.rentrix.rentrixserver.tenancy.RentRecordRepository;
import com.rentrix.rentrixserver.tenancy.RentRecordStatus;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyService;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.ApprovalGuard;
import com.rentrix.rentrixserver.verification.VerificationApplication;
import com.rentrix.rentrixserver.verification.VerificationApplicationRepository;
import com.rentrix.rentrixserver.verification.VerificationEventRepository;
import com.rentrix.rentrixserver.verification.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev demo seed (D-68, D-69) on its own empty database: 4 + 4 published demo homes with demo landlords,
 * no phones, {@code example.test} emails, and on every home a confirmed tenancy and a rent history with at least
 * one change (PLAN B5), and reviews in all three directions with their aggregates (PLAN B6); it never runs twice
 * and its bean does not exist outside {@code dev}.
 * Verification states (D-97, D-115): a demo admin, every demo party approved and ID verified, and an admin queue.
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
	TenancyRepository tenancies;
	
	@Autowired
	RentRecordRepository rentRecords;
	
	@Autowired
	ReviewRepository reviews;
	
	@Autowired
	ReviewResponseRepository responses;
	
	@Autowired
	RatingAggregateRepository aggregates;
	
	@Autowired
	RatingRecomputer recomputer;
	
	@Autowired
	Clock clock;
	
	@Autowired
	PlatformTransactionManager transactionManager;
	
	@Autowired
	ApplicationContext context;
	
	@Autowired
	VerificationApplicationRepository applications;
	
	@Autowired
	VerificationEventRepository events;
	
	@Autowired
	IdentityVerificationRepository identityChecks;
	
	@Autowired
	ModerationActionRepository audit;
	
	@Autowired
	IdHasher hasher;
	
	@Autowired
	IdentityService identity;
	
	@Autowired
	ApprovalGuard guard;
	
	@Test
	void seedsAnEmptyDatabaseOnceWithClearlyFakeData() {
		assertThat(context.getBeansOfType(DemoSeeder.class)).as("dev-only bean").isEmpty();
		DemoSeeder seeder = new DemoSeeder(users, properties, cities, areas, societies, towers, tenancies, rentRecords,
			applications, events, identityChecks, audit, hasher, reviews, responses, recomputer, clock,
			transactionManager);
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
			// 4 landlords + 8 tenants with homes, the demo admin and 4 verification-queue users
			assertThat(people).hasSize(17).allMatch(User::isDemo)
				.allMatch(u -> u.getEmail().endsWith("@example.test"))
				.allMatch(u -> u.getPhone() == null && u.getGoogleSub() == null)
				.allMatch(u -> u.getEmailVerifiedAt() != null && u.getOnboardedAt() != null);
			
			List<Tenancy> all = tenancies.findAll();
			assertThat(all).hasSize(25).allMatch(t -> !t.getStartDate().isAfter(LocalDate.now()))
				.allMatch(t -> t.getLandlord().getId().equals(t.getProperty().getLandlord().getId()));
			assertThat(all.stream().filter(t -> t.getStatus() == TenancyStatus.PENDING_CONFIRMATION)).hasSize(1);
			for (Property home : homes) {
				List<Tenancy> confirmed = all.stream()
					.filter(t -> t.getProperty().getId().equals(home.getId()))
					.filter(t -> t.getStatus() == TenancyStatus.ACTIVE || t.getStatus() == TenancyStatus.ENDED)
					.toList();
				assertThat(confirmed).as(home.getBuildingName()).isNotEmpty();
				if (TenancyService.wholeHome(home)) {
					// One tenancy at a time on a whole home (D-23): no two confirmed periods overlap.
					List<Tenancy> byStart = confirmed.stream().sorted(Comparator.comparing(Tenancy::getStartDate)).toList();
					for (int i = 0; i + 1 < byStart.size(); i++) {
						Tenancy earlier = byStart.get(i);
						assertThat(earlier.getEndDate()).as("overlap at " + home.getBuildingName()).isNotNull()
							.isBefore(byStart.get(i + 1).getStartDate());
					}
				}
				List<RentRecord> history = rentRecords.findByTenancyPropertyIdAndStatusAndTenancyStatusInOrderByEffectiveFromAsc(
					home.getId(), RentRecordStatus.CONFIRMED, List.of(TenancyStatus.ACTIVE, TenancyStatus.ENDED));
				assertThat(history.stream().map(RentRecord::getMonthlyRentInr).distinct().count())
					.as("rent change at " + home.getBuildingName()).isGreaterThanOrEqualTo(2);
				assertThat(history).allMatch(r -> r.getEffectiveFrom().getDayOfMonth() == 1);
			}
			assertThat(all.stream().filter(t -> t.getStatus() == TenancyStatus.ACTIVE).map(t -> t.getTenant().getId())
				.distinct().count()).as("one current home per tenant").isEqualTo(8);
			
			User admin = users.findByEmail("demo-admin@example.test").orElseThrow();
			assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
			
			Map<String, VerificationApplication> byName = applications.findAll().stream()
				.collect(Collectors.toMap(a -> a.getUser().getDisplayName() + "/" + a.getRole(), a -> a));
			for (String code : DemoSeeder.LANDLORDS) {
				assertApprovedAndIdVerified(byName.get("Demo Landlord " + code + "/LANDLORD"));
			}
			for (String code : DemoSeeder.TENANTS) {
				assertApprovedAndIdVerified(byName.get("Demo Tenant " + code + "/TENANT"));
			}
			assertThat(byName.get("Demo Landlord BLR-3/LANDLORD").getStatus()).isEqualTo(VerificationStatus.PENDING);
			assertThat(byName.get("Demo Tenant PNQ-7/TENANT").getStatus()).isEqualTo(VerificationStatus.PENDING);
			VerificationApplication blr7 = byName.get("Demo Tenant BLR-7/TENANT");
			assertThat(blr7.getStatus()).isEqualTo(VerificationStatus.PENDING);
			assertThat(blr7.flagSet()).containsExactly("DUPLICATE_ID");
			VerificationApplication blr8 = byName.get("Demo Tenant BLR-8/TENANT");
			assertThat(blr8.getStatus()).isEqualTo(VerificationStatus.REJECTED);
			assertThat(blr8.getDecisionNote()).isEqualTo("The PAN photo is blurred. Upload a sharper photo.");
			assertThat(applications.findAll().stream().filter(a -> a.getStatus() == VerificationStatus.PENDING))
				.as("admin queue").hasSize(3);
			
			// PNQ-7 shows "ID verified" from the phone while the application waits (D-105).
			User pnq7 = byName.get("Demo Tenant PNQ-7/TENANT").getUser();
			assertThat(identity.isIdVerified(pnq7)).isTrue();
			assertThat(identity.isIdVerified(blr7.getUser())).isFalse();
			
			// Only masked data: last 4 of at most 4 characters, never a full number.
			List<IdentityVerification> rows = identityChecks.findAll();
			List<String> linkedinVerified = new ArrayList<>();
			for (IdentityVerification row : rows) {
				if (row.getMethod() == IdentityMethod.LINKEDIN) {
					if (row.getStatus() == IdentityStatus.VERIFIED) {
						linkedinVerified.add(row.getUser().getDisplayName());
					}
				} else {
					assertThat(row.getLast4()).hasSize(4);
				}
			}
			assertThat(linkedinVerified).contains("Demo Landlord BLR-1", "Demo Landlord PNQ-2")
				.isSubsetOf("Demo Landlord BLR-1", "Demo Landlord PNQ-2", "Demo Tenant PNQ-2");
			// Every decision has its audit row by the demo admin.
			assertThat(audit.findAll()).isNotEmpty().allMatch(a -> a.getAdmin().getId().equals(admin.getId()));
			
			// PLAN B6: reviews in all three directions on every home, spread 1-5, one awaiting reveal, one response
			List<Review> written = reviews.findAll();
			assertThat(written).hasSize(70).allSatisfy(r ->
				assertThat(ReviewContentRules.review(r.getBody(), r.getProperty().getUnitLabel())).isEqualTo(r.getBody()));
			assertThat(written.stream().map(Review::getRatingOverall).distinct()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
			for (Property home : homes) {
				assertThat(written.stream().filter(r -> r.getProperty().getId().equals(home.getId()))
					.filter(r -> r.getStatus() == ReviewStatus.PUBLISHED).map(Review::getDirection).distinct())
					.as("published directions at " + home.getBuildingName())
					.containsExactlyInAnyOrder(ReviewDirection.values());
			}
			assertThat(written).filteredOn(r -> r.getStatus() == ReviewStatus.AWAITING_REVEAL).singleElement()
				.satisfies(r -> {
					assertThat(r.getDirection()).isEqualTo(ReviewDirection.LANDLORD_TO_TENANT);
					assertThat(r.getProperty().getBuildingName()).isEqualTo("Demo Villa C");
				});
			assertThat(responses.findAll()).hasSize(1);
			// every home but Demo Villa C (its current tenant has not reviewed yet) and every landlord shows an average
			for (Property home : homes) {
				RatingAggregate rating = aggregates.findById(new RatingAggregateId(RatingSubjectType.PROPERTY,
					home.getId())).orElseThrow();
				assertThat(rating.getReviewCount()).as(home.getBuildingName())
					.isEqualTo(home.getBuildingName().equals("Demo Villa C") ? 2 : 3);
				assertThat(rating.getBayesScore()).isNotNull();
			}
			for (User landlord : people.stream().filter(u -> DemoSeeder.LANDLORDS.stream()
				.anyMatch(code -> u.getDisplayName().equals("Demo Landlord " + code))).toList()) {
				assertThat(aggregates.findById(new RatingAggregateId(RatingSubjectType.LANDLORD, landlord.getId()))
					.orElseThrow().getReviewCount()).as(landlord.getDisplayName()).isEqualTo(4);
			}
			assertThat(written.stream().filter(Review::isCountsInAggregate)
				.allMatch(r -> r.getStatus() == ReviewStatus.PUBLISHED)).isTrue();
			assertThat(EnumSet.copyOf(written.stream().filter(r -> !r.isCountsInAggregate()
				&& r.getStatus() == ReviewStatus.PUBLISHED).map(Review::getDirection).toList()))
				.as("repeat pairs only count once (D-44)").containsExactly(ReviewDirection.TENANT_TO_LANDLORD,
					ReviewDirection.LANDLORD_TO_TENANT);
		});
	}
	
	private void assertApprovedAndIdVerified(VerificationApplication application) {
		assertThat(application).isNotNull();
		assertThat(application.getStatus()).isEqualTo(VerificationStatus.APPROVED);
		assertThat(identity.isIdVerified(application.getUser())).as(application.getUser().getDisplayName()).isTrue();
		assertThat(guard.isApproved(application.getUser(), application.getRole())).isTrue();
	}
	
}
