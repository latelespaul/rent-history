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
