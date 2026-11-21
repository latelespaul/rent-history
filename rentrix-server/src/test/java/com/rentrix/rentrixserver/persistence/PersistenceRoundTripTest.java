package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.admin.ModerationAction;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.auth.RefreshToken;
import com.rentrix.rentrixserver.common.scheduling.ScheduledJobLock;
import com.rentrix.rentrixserver.enquiry.Enquiry;
import com.rentrix.rentrixserver.property.Furnishing;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyImage;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.report.Report;
import com.rentrix.rentrixserver.report.ReportReason;
import com.rentrix.rentrixserver.report.ReportTargetType;
import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.RatingAggregateId;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewResponse;
import com.rentrix.rentrixserver.review.ReviewRevision;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.DocumentStatus;
import com.rentrix.rentrixserver.tenancy.EvidenceLevel;
import com.rentrix.rentrixserver.tenancy.InviteToken;
import com.rentrix.rentrixserver.tenancy.RentRecord;
import com.rentrix.rentrixserver.tenancy.RentRecordStatus;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyDocument;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.ShareLink;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Every entity writes and reads back through the real SQLite schema. */
@IntegrationTest
@Transactional
class PersistenceRoundTripTest {
	
	private static final Instant WHEN = Instant.parse("2024-03-05T06:07:08.123Z");
	
	@Autowired
	EntityManager em;
	
	@Autowired
	JdbcTemplate jdbc;
	
	@Autowired
	TestData data;
	
	@Test
	void everyEntityRoundTrips() {
		User landlord = data.user("Demo Landlord", Role.LANDLORD);
		User tenant = data.user("Demo Tenant", Role.TENANT, Role.LANDLORD);
		tenant.setEmailVerifiedAt(WHEN);
		tenant.setHomeCity(data.city("PNQ"));
		Property property = data.property(landlord, "BLR", "  Koramangala   5th  Block ", PropertyStatus.PUBLISHED);
		property.setFurnishing(Furnishing.SEMI);
		property.setParking(true);
		property.setBedrooms(2);
		Tenancy tenancy = data.tenancy(property, tenant, LocalDate.of(2024, 5, 1), LocalDate.of(2025, 4, 30),
			TenancyStatus.ENDED);
		tenancy.setEvidenceLevel(EvidenceLevel.MUTUAL);
		tenancy.setDepositInr(100_000L);
		tenancy.setConfirmedAt(WHEN);
		
		RentRecord rent = new RentRecord();
		rent.setTenancy(tenancy);
		rent.setEffectiveFrom(LocalDate.of(2024, 5, 1));
		rent.setMonthlyRentInr(32_000);
		rent.setAddedBy(landlord);
		rent.setStatus(RentRecordStatus.CONFIRMED);
		em.persist(rent);
		
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(ReviewDirection.TENANT_TO_LANDLORD);
		review.setAuthor(tenant);
		review.setSubjectUser(landlord);
		review.setProperty(property);
		review.setRatingOverall(4);
		review.setRatingB(5);
		review.setBody("Responsive about repairs and returned the deposit on time.");
		review.setStatus(ReviewStatus.PUBLISHED);
		review.setSubmittedAt(WHEN);
		review.setPublishedAt(WHEN);
		em.persist(review);
		
		ReviewResponse response = new ReviewResponse();
		response.setReview(review);
		response.setAuthor(landlord);
		response.setBody("Thank you for the kind words.");
		em.persist(response);
		
		ReviewRevision revision = new ReviewRevision();
		revision.setReview(review);
		revision.setRevisionNo(1);
		revision.setRatingOverall(3);
		revision.setBody("First version of the review text, kept for admins.");
		em.persist(revision);
		
		RatingAggregate aggregate = new RatingAggregate(RatingSubjectType.LANDLORD, landlord.getId());
		aggregate.setReviewCount(3);
		aggregate.setRatingSum(13);
		aggregate.setAverage(new BigDecimal("4.33"));
		aggregate.setBayesScore(new BigDecimal("3.813"));
		aggregate.setCount4(2);
		aggregate.setCount5(1);
		aggregate.setLastReviewAt(WHEN);
		em.persist(aggregate);
		
		Enquiry enquiry = new Enquiry();
		enquiry.setTenant(tenant);
		enquiry.setProperty(property);
		enquiry.setMessage("Is the flat available from next month?");
		enquiry.setShareContact(true);
		enquiry.setExpiresAt(WHEN.plusSeconds(90L * 24 * 3600));
		em.persist(enquiry);
		
		Report report = new Report();
		report.setReporter(landlord);
		report.setTargetType(ReportTargetType.REVIEW);
		report.setTargetId(review.getId());
		report.setReason(ReportReason.OFF_TOPIC);
		em.persist(report);
		
		ModerationAction action = new ModerationAction();
		action.setAdmin(landlord);
		action.setAction(ModerationActionType.DISMISS_REPORT);
		action.setTargetType(ReportTargetType.REVIEW);
		action.setTargetId(review.getId());
		action.setReport(report);
		em.persist(action);
		
		RefreshToken refresh = new RefreshToken();
		refresh.setUser(tenant);
		refresh.setTokenHash("a".repeat(64));
		refresh.setFamilyId(UUID.randomUUID());
		refresh.setExpiresAt(WHEN);
		em.persist(refresh);
		
		ShareLink share = new ShareLink();
		share.setUser(tenant);
		share.setTokenHash("b".repeat(64));
		share.setExpiresAt(WHEN);
		em.persist(share);
		
		InviteToken invite = new InviteToken();
		invite.setTenancy(tenancy);
		invite.setTokenHash("c".repeat(64));
		invite.setEmail("invitee@example.test");
		invite.setExpiresAt(WHEN);
		em.persist(invite);
		
		TenancyDocument document = new TenancyDocument();
		document.setTenancy(tenancy);
		document.setUploadedBy(tenant);
		document.setStorageKey("tenancies/x/agreement.pdf");
		document.setContentType("application/pdf");
		document.setSizeBytes(12_345);
		document.setSha256("d".repeat(64));
		em.persist(document);
		
		PropertyImage image = new PropertyImage();
		image.setProperty(property);
		image.setStorageKey("properties/x/1.webp");
		image.setThumbKey("properties/x/1-thumb.webp");
		image.setContentType("image/webp");
		image.setWidth(2048);
		image.setHeight(1536);
		image.setSizeBytes(250_000);
		image.setPrimary(true);
		em.persist(image);
		
		ScheduledJobLock lock = new ScheduledJobLock();
		lock.setName("review-reveal");
		lock.setLockUntil(WHEN);
		lock.setLockedAt(WHEN);
		lock.setLockedBy("test");
		em.persist(lock);
		
		em.flush();
		em.clear();
		
		User loadedTenant = em.find(User.class, tenant.getId());
		assertThat(loadedTenant.getPublicId()).isEqualTo(tenant.getPublicId());
		assertThat(loadedTenant.getRoles()).containsExactlyInAnyOrder(Role.TENANT, Role.LANDLORD);
		assertThat(loadedTenant.getEmailVerifiedAt()).isEqualTo(WHEN);
		assertThat(loadedTenant.getHomeCity().getCode()).isEqualTo("PNQ");
		assertThat(loadedTenant.getCreatedAt()).isNotNull();
		
		Property loadedProperty = em.find(Property.class, property.getId());
		assertThat(loadedProperty.getLocality()).isEqualTo("Koramangala   5th  Block");
		assertThat(loadedProperty.getLocalityKey()).isEqualTo("koramangala 5th block");
		assertThat(loadedProperty.getFurnishing()).isEqualTo(Furnishing.SEMI);
		assertThat(loadedProperty.getParking()).isTrue();
		assertThat(loadedProperty.getBedrooms()).isEqualTo(2);
		
		Tenancy loadedTenancy = em.find(Tenancy.class, tenancy.getId());
		assertThat(loadedTenancy.getStartDate()).isEqualTo(LocalDate.of(2024, 5, 1));
		assertThat(loadedTenancy.getEndDate()).isEqualTo(LocalDate.of(2025, 4, 30));
		assertThat(loadedTenancy.getDepositInr()).isEqualTo(100_000L);
		assertThat(loadedTenancy.getStatus()).isEqualTo(TenancyStatus.ENDED);
		assertThat(loadedTenancy.getConfirmedAt()).isEqualTo(WHEN);
		
		assertThat(em.find(RentRecord.class, rent.getId()).getEffectiveFrom()).isEqualTo(LocalDate.of(2024, 5, 1));
		Review loadedReview = em.find(Review.class, review.getId());
		assertThat(loadedReview.getRatingOverall()).isEqualTo(4);
		assertThat(loadedReview.getRatingA()).isNull();
		assertThat(loadedReview.getRatingB()).isEqualTo(5);
		assertThat(loadedReview.isCountsInAggregate()).isTrue();
		assertThat(loadedReview.getPublishedAt()).isEqualTo(WHEN);
		assertThat(em.find(ReviewResponse.class, response.getId()).getReview().getId()).isEqualTo(review.getId());
		assertThat(em.find(ReviewRevision.class, revision.getId()).getRatingOverall()).isEqualTo(3);
		
		RatingAggregate loadedAggregate = em.find(RatingAggregate.class,
			new RatingAggregateId(RatingSubjectType.LANDLORD, landlord.getId()));
		assertThat(loadedAggregate.getAverage()).isEqualByComparingTo("4.33");
		assertThat(loadedAggregate.getBayesScore()).isEqualByComparingTo("3.813");
		assertThat(loadedAggregate.getCount4()).isEqualTo(2);
		
		assertThat(em.find(Enquiry.class, enquiry.getId()).isShareContact()).isTrue();
		assertThat(em.find(Report.class, report.getId()).getTargetId()).isEqualTo(review.getId());
		assertThat(em.find(ModerationAction.class, action.getId()).getReport().getId()).isEqualTo(report.getId());
		assertThat(em.find(RefreshToken.class, refresh.getId()).getFamilyId()).isEqualTo(refresh.getFamilyId());
		assertThat(em.find(ShareLink.class, share.getId()).getExpiresAt()).isEqualTo(WHEN);
		assertThat(em.find(InviteToken.class, invite.getId()).getEmail()).isEqualTo("invitee@example.test");
		assertThat(em.find(TenancyDocument.class, document.getId()).getStatus()).isEqualTo(DocumentStatus.UPLOADED);
		assertThat(em.find(PropertyImage.class, image.getId()).isPrimary()).isTrue();
		assertThat(em.find(ScheduledJobLock.class, "review-reveal").getLockUntil()).isEqualTo(WHEN);
	}
	
	@Test
	void timestampsAndDatesAreStoredAsUtcText() {
		User user = data.user("Demo Clock");
		user.setEmailVerifiedAt(WHEN);
		Property property = data.property(user, "PNQ", "Baner", PropertyStatus.DRAFT);
		data.tenancy(property, data.user("Demo Renter"), LocalDate.of(2024, 5, 1), null, TenancyStatus.ACTIVE);
		em.flush();
		
		String verified = jdbc.queryForObject("select email_verified_at from users where id = ?", String.class,
			user.getId());
		assertThat(verified).isEqualTo(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
			.withZone(ZoneOffset.UTC).format(WHEN));
		String start = jdbc.queryForObject("select start_date from tenancies where property_id = ?", String.class,
			property.getId());
		assertThat(start).startsWith("2024-05-01");
	}
	
}
