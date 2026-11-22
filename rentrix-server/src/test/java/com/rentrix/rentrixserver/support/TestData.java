package com.rentrix.rentrixserver.support;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.identity.CheckedBy;
import com.rentrix.rentrixserver.identity.IdentityMethod;
import com.rentrix.rentrixserver.identity.IdentityStatus;
import com.rentrix.rentrixserver.identity.IdentityVerification;
import com.rentrix.rentrixserver.identity.IdentityVerificationRepository;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.VerificationApplication;
import com.rentrix.rentrixserver.verification.VerificationApplicationRepository;
import com.rentrix.rentrixserver.verification.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

/** Builds valid rows with unique values, so tests need no cleanup between them. */
@Component
@RequiredArgsConstructor
public class TestData {
	
	private final UserRepository users;
	private final CityRepository cities;
	private final PropertyRepository properties;
	private final TenancyRepository tenancies;
	private final VerificationApplicationRepository applications;
	private final IdentityVerificationRepository identityChecks;
	
	public User user(String name, Role... roles) {
		User user = new User();
		user.setDisplayName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRoles(roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.of(roles[0], roles));
		return users.save(user);
	}
	
	/** A user with a verified email whose verification is not approved (DRAFT). */
	public User verified(String name, Role... roles) {
		User user = user(name, roles);
		user.setEmailVerifiedAt(Instant.parse("2025-01-01T00:00:00Z"));
		return users.save(user);
	}
	
	/** An approved user (green tick per role, D-134) with a verified phone, so they may post (D-132): see {@link #approve(User)}. */
	public User approved(String name, Role... roles) {
		User user = verified(name, roles);
		approve(user);
		return user;
	}
	
	/**
	 * Writes an {@code APPROVED} application for each held {@code TENANT} / {@code LANDLORD} role and a
	 * {@code VERIFIED} phone check ({@link #verifyPhone(User)}), so {@code ApprovalGuard} lets the user write. The
	 * user row itself is not saved.
	 */
	public void approve(User user) {
		for (Role role : new Role[] {Role.TENANT, Role.LANDLORD}) {
			if (!user.hasRole(role)) {
				continue;
			}
			VerificationApplication application = applications.findByUserAndRole(user, role).orElseGet(() -> {
				VerificationApplication created = new VerificationApplication();
				created.setUser(user);
				created.setRole(role);
				return created;
			});
			application.setStatus(VerificationStatus.APPROVED);
			application.setSubmittedAt(Instant.parse("2025-01-02T00:00:00Z"));
			application.setDecidedAt(Instant.parse("2025-01-03T00:00:00Z"));
			applications.save(application);
		}
		verifyPhone(user);
	}
	
	/** A {@code VERIFIED} phone check (D-117): all a user needs to post (D-132). The user row itself is not saved. */
	public void verifyPhone(User user) {
		IdentityVerification phone = identityChecks.findByUserAndMethod(user, IdentityMethod.PHONE_OTP)
			.orElseGet(IdentityVerification::new);
		if (phone.getStatus() != IdentityStatus.VERIFIED) {
			phone.setUser(user);
			phone.setMethod(IdentityMethod.PHONE_OTP);
			phone.setStatus(IdentityStatus.VERIFIED);
			phone.setLast4("0000");
			phone.setCheckedBy(CheckedBy.OTP);
			phone.setVerifiedAt(Instant.parse("2025-01-02T00:00:00Z"));
			identityChecks.save(phone);
		}
	}
	
	public City city(String code) {
		return cities.findById(code).orElseThrow();
	}
	
	public Property property(User landlord, String cityCode, String locality, PropertyStatus status) {
		Property property = new Property();
		property.setLandlord(landlord);
		property.setCity(city(cityCode));
		property.setLocality(locality);
		property.setPincode(cityCode.equals("BLR") ? "560034" : "411045");
		property.setPropertyType(PropertyType.APARTMENT);
		property.setStatus(status);
		return properties.save(property);
	}
	
	public Tenancy tenancy(Property property, User tenant, LocalDate start, LocalDate end, TenancyStatus status) {
		Tenancy tenancy = new Tenancy();
		tenancy.setProperty(property);
		tenancy.setLandlord(property.getLandlord());
		tenancy.setTenant(tenant);
		tenancy.setStartDate(start);
		tenancy.setEndDate(end);
		tenancy.setInitialRentInr(25_000);
		tenancy.setInitiatedBy(TenancyParty.LANDLORD);
		tenancy.setStatus(status);
		return tenancies.save(tenancy);
	}
	
}
