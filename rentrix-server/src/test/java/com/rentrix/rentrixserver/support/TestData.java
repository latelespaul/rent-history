package com.rentrix.rentrixserver.support;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.city.CityRepository;
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
	
	public User user(String name, Role... roles) {
		User user = new User();
		user.setDisplayName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRoles(roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.of(roles[0], roles));
		return users.save(user);
	}
	
	/** A user with a verified email whose verification is not approved (DRAFT), like every real user before B11. */
	public User verified(String name, Role... roles) {
		User user = user(name, roles);
		user.setEmailVerifiedAt(Instant.parse("2025-01-01T00:00:00Z"));
		return users.save(user);
	}
	
	/**
	 * A user who passes the approval gate (D-91). Until B11 the interim statuses approve demo users only, so the
	 * fixture marks the user as demo; B11 changes this fixture to write an approved application instead.
	 */
	public User approved(String name, Role... roles) {
		User user = verified(name, roles);
		user.setDemo(true);
		return users.save(user);
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
