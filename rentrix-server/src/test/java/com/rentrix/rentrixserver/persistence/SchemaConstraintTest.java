package com.rentrix.rentrixserver.persistence;

import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

/** The trust rules the schema itself enforces (D-27, D-35, D-38) and that FKs are on. */
@IntegrationTest
@Transactional
class SchemaConstraintTest {
	
	@Autowired
	TestData data;
	
	@Autowired
	ReviewRepository reviews;
	
	@Autowired
	JdbcTemplate jdbc;
	
	private Tenancy tenancy() {
		User landlord = data.user("Demo Landlord", Role.LANDLORD);
		Property property = data.property(landlord, "BLR", "Indiranagar", PropertyStatus.PUBLISHED);
		return data.tenancy(property, data.user("Demo Tenant", Role.TENANT), LocalDate.of(2024, 1, 1), null,
			TenancyStatus.ACTIVE);
	}
	
	private Review review(Tenancy tenancy, ReviewDirection direction, int rating) {
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(direction);
		review.setAuthor(tenancy.getTenant());
		review.setSubjectUser(tenancy.getLandlord());
		review.setProperty(tenancy.getProperty());
		review.setRatingOverall(rating);
		review.setBody("A fair landlord who fixed things quickly and kept in touch.");
		review.setSubmittedAt(Instant.now());
		return review;
	}
	
	@Test
	void oneReviewPerDirectionPerTenancy() {
		Tenancy tenancy = tenancy();
		reviews.saveAndFlush(review(tenancy, ReviewDirection.TENANT_TO_LANDLORD, 4));
		reviews.saveAndFlush(review(tenancy, ReviewDirection.TENANT_TO_PROPERTY, 4));
		assertThatThrownBy(() -> reviews.saveAndFlush(review(tenancy, ReviewDirection.TENANT_TO_LANDLORD, 2)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
	
	@Test
	void ratingMustBeOneToFive() {
		Tenancy tenancy = tenancy();
		assertThatThrownBy(() -> reviews.saveAndFlush(review(tenancy, ReviewDirection.TENANT_TO_LANDLORD, 6)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
	
	@Test
	void landlordCannotBeTheirOwnTenant() {
		User landlord = data.user("Demo Landlord", Role.LANDLORD, Role.TENANT);
		Property property = data.property(landlord, "PNQ", "Kothrud", PropertyStatus.PUBLISHED);
		assertThatThrownBy(() -> {
			data.tenancy(property, landlord, LocalDate.of(2024, 1, 1), null, TenancyStatus.ACTIVE);
			reviews.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}
	
	@Test
	void foreignKeysAreEnforced() {
		assertThatThrownBy(() -> jdbc.update("insert into user_roles (user_id, role) values (?, 'TENANT')", 42L))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
	
}
