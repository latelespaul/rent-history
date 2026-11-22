package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Builds location rows and placed homes for location tests (call inside a rolled-back test transaction). */
@Component
@RequiredArgsConstructor
public class LocationTestSupport {
	
	private final AreaRepository areas;
	private final SocietyRepository societies;
	private final SocietyTowerRepository towers;
	private final CityRepository cities;
	private final PropertyRepository properties;
	
	public Area area(String city, String name, String aliases, LocationStatus status, User createdBy,
		String... pincodes) {
		Area area = new Area();
		area.setCity(cities.getReferenceById(city));
		area.setName(name);
		area.setAliases(aliases);
		List.of(pincodes).forEach(area::addPincode);
		area.setStatus(status);
		area.setCreatedBy(createdBy);
		return areas.saveAndFlush(area);
	}
	
	public Area area(String city, String name, String... pincodes) {
		return area(city, name, null, LocationStatus.ACTIVE, null, pincodes);
	}
	
	public Society society(Area area, String name, String developer, SocietyKind kind, LocationStatus status,
		User createdBy) {
		Society society = new Society();
		society.setArea(area);
		society.setName(name);
		society.setDeveloper(developer);
		society.setKind(kind);
		society.setStatus(status);
		society.setCreatedBy(createdBy);
		return societies.saveAndFlush(society);
	}
	
	public Society society(Area area, String name) {
		return society(area, name, null, SocietyKind.GATED_COMMUNITY, LocationStatus.ACTIVE, null);
	}
	
	/** A tower with the given wings, all with the tower's status. */
	public SocietyTower tower(Society society, String name, int order, LocationStatus status, User createdBy,
		String... wings) {
		SocietyTower tower = new SocietyTower();
		tower.setSociety(society);
		tower.setName(name);
		tower.setSortOrder(order);
		tower.setStatus(status);
		tower.setCreatedBy(createdBy);
		for (String wing : wings) {
			tower.addWing(wing, status, createdBy);
		}
		return towers.saveAndFlush(tower);
	}
	
	public SocietyTower tower(Society society, String name, int order, String... wings) {
		return tower(society, name, order, LocationStatus.ACTIVE, null, wings);
	}
	
	public static SocietyWing wing(SocietyTower tower, String name) {
		return tower.getWings().stream().filter(w -> w.getName().equals(name)).findFirst().orElseThrow();
	}
	
	/** A home in the area (and society / tower / wing when given), locality copied from the area. */
	public Property home(User landlord, String building, PropertyStatus status, Area area, Society society,
		SocietyTower tower, SocietyWing wing) {
		Property property = new Property();
		property.setLandlord(landlord);
		property.setCity(area.getCity());
		property.setArea(area);
		property.setSociety(society);
		property.setTower(tower);
		property.setWing(wing);
		property.setLocality(area.getName());
		property.setBuildingName(building);
		property.setPincode(area.pincodeList().getFirst());
		property.setPropertyType(PropertyType.APARTMENT);
		property.setStatus(status);
		return properties.saveAndFlush(property);
	}
	
}
