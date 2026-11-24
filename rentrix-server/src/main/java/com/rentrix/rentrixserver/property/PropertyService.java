package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.persistence.TimestampedEntity;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.edit.EditHistory;
import com.rentrix.rentrixserver.home.dto.UpdateHomeRequest;
import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationInput;
import com.rentrix.rentrixserver.location.Placement;
import com.rentrix.rentrixserver.location.LocationService;
import com.rentrix.rentrixserver.location.LocationStatus;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.property.dto.CreatePropertyRequest;
import com.rentrix.rentrixserver.property.dto.LandlordSummary;
import com.rentrix.rentrixserver.property.dto.OwnerContact;
import com.rentrix.rentrixserver.property.dto.PropertyCard;
import com.rentrix.rentrixserver.property.dto.PropertyDetail;
import com.rentrix.rentrixserver.property.dto.PropertyLocation;
import com.rentrix.rentrixserver.property.dto.RatingSummary;
import com.rentrix.rentrixserver.property.dto.UpdatePropertyRequest;
import com.rentrix.rentrixserver.review.RatingAggregate;
import com.rentrix.rentrixserver.review.RatingAggregateId;
import com.rentrix.rentrixserver.review.RatingAggregateRepository;
import com.rentrix.rentrixserver.review.RatingSubjectType;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.user.PublicNames;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.GreenTicks;
import com.rentrix.rentrixserver.verification.VerificationState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Properties (§5.3, §12.2): landlord CRUD with the listing states of D-92 and the public search. Visibility
 * (D-21): {@code PUBLISHED} and {@code ARCHIVED} rows open for anyone by link, search lists only
 * {@code PUBLISHED}, every other state is visible to its owner and admins only and is {@code 404} to others.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyService {
	
	private final PropertyRepository properties;
	private final RatingAggregateRepository aggregates;
	private final UserRepository users;
	private final CityRepository cities;
	private final CityService cityService;
	private final LocationService locations;
	private final JdbcTemplate jdbc;
	private final GreenTicks greenTicks;
	
	// ---------------------------------------------------------------- reads
	
	/** The location filters of {@code GET /properties} as given ({@code public_id}s, D-112); all optional. */
	public record LocationFilter(UUID area, UUID society, UUID tower, Boolean standalone) {
	}
	
	public PageResponse<PropertyCard> search(PropertySearchCriteria criteria, LocationFilter location,
		AuthenticatedUser caller, Pageable pageable) {
		return search(withLocation(criteria, location, caller), pageable);
	}
	
	public PageResponse<PropertyCard> search(PropertySearchCriteria criteria, Pageable pageable) {
		if (criteria.cityCode() != null) {
			cityService.requireLive(criteria.cityCode());
		}
		PropertySearchQuery query = PropertySearchQuery.of(criteria);
		Sort.Order order = pageable.getSort().iterator().next();
		long total = Objects.requireNonNull(jdbc.queryForObject(query.countSql(), Long.class, query.params()));
		List<Long> ids = total == 0 ? List.of() : jdbc.queryForList(
			query.pageSql(order.getProperty(), order.isDescending()), Long.class,
			query.pageParams(pageable.getPageSize(), pageable.getOffset()));
		Map<Long, Property> byId = properties.findByIdIn(ids).stream()
			.collect(Collectors.toMap(Property::getId, Function.identity()));
		Map<Long, RatingAggregate> ratings = aggregates(RatingSubjectType.PROPERTY, ids);
		List<Property> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
		Page<Property> page = new PageImpl<>(ordered, pageable, total);
		return PageResponse.of(page, p -> card(p, ratings.get(p.getId())));
	}
	
	public PropertyDetail get(UUID publicId, AuthenticatedUser caller) {
		Property property = visible(publicId, caller);
		return detail(property, caller);
	}
	
	/** {@code GET /me/properties}: the caller's own listings in every state. */
	public PageResponse<PropertyDetail> mine(AuthenticatedUser caller, Pageable pageable) {
		User owner = currentUser(caller);
		Page<Property> page = properties.findByLandlordIdAndDeletedAtIsNull(owner.getId(), pageable);
		List<Long> ids = page.getContent().stream().map(Property::getId).toList();
		Map<Long, RatingAggregate> ratings = aggregates(RatingSubjectType.PROPERTY, ids);
		RatingSummary landlordRating = RatingSummary.of(aggregate(RatingSubjectType.LANDLORD, owner.getId()));
		boolean landlordVerified = greenTicks.has(owner, Role.LANDLORD);
		return PageResponse.of(page, p -> detail(p, RatingSummary.of(ratings.get(p.getId())), landlordRating,
			landlordVerified, true, true, createdBy(p, owner.getPublicId())));
	}
	
	/**
	 * Resolves the location filters to ids. Unknown ids, and someone else's {@code PENDING} proposal that holds
	 * none of the caller's homes (D-109), are {@code 422} on the parameter; a tower outside the society, a society outside the area, or any of them outside
	 * the requested city is {@code 422 location_mismatch}; their city must be live (D-20).
	 */
	private PropertySearchCriteria withLocation(PropertySearchCriteria criteria, LocationFilter location,
		AuthenticatedUser caller) {
		if (location == null || location.area() == null && location.society() == null && location.tower() == null) {
			return location == null || location.standalone() == null ? criteria : copy(criteria, null, null, null,
				location.standalone());
		}
		SocietyTower tower = location.tower() == null ? null
			: locations.towerFilter(location.tower(), caller, "tower");
		Society society = location.society() == null ? null
			: locations.societyFilter(location.society(), caller, "society");
		if (tower != null && society != null && !tower.getSociety().getId().equals(society.getId())) {
			throw locationMismatch("tower", "This tower is not in this society.");
		}
		if (society == null && tower != null) {
			society = tower.getSociety();
		}
		Area area = location.area() == null ? null : locations.areaFilter(location.area(), caller, "area");
		if (area != null && society != null && !society.getArea().getId().equals(area.getId())) {
			throw locationMismatch("society", "This society is not in this area.");
		}
		String cityCode = society != null ? society.getCity().getCode() : area.getCity().getCode();
		if (criteria.cityCode() != null && !criteria.cityCode().equals(cityCode)) {
			throw locationMismatch(society != null ? "society" : "area", "This place is not in that city.");
		}
		cityService.requireLive(cityCode);
		return copy(criteria, area == null ? null : area.getId(), society == null ? null : society.getId(),
			tower == null ? null : tower.getId(), location.standalone());
	}
	
	private static PropertySearchCriteria copy(PropertySearchCriteria c, Long areaId, Long societyId, Long towerId,
		Boolean standalone) {
		return new PropertySearchCriteria(c.cityCode(), c.localityPrefix(), c.type(), c.bedrooms(), c.minRating(),
			c.text(), areaId, societyId, towerId, standalone, c.homeSize());
	}
	
	private static ApiException locationMismatch(String field, String detail) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "location_mismatch", detail,
			List.of(new ApiProblem.FieldError(field, "mismatch", detail)));
	}
	
	// --------------------------------------------------------------- writes
	
	/** {@code POST /properties}: a new {@code DRAFT} owned by the calling landlord (D-21). */
	@Transactional
	public PropertyDetail create(CreatePropertyRequest request, AuthenticatedUser caller) {
		User landlord = currentUser(caller);
		Property property = build(request, landlord, landlord, false, false);
		property.setStatus(PropertyStatus.DRAFT);
		Property saved = properties.saveAndFlush(property);
		return detail(saved, caller);
	}
	
	/**
	 * A home added through {@code POST /me/homes} (D-139, D-140): open by default it is {@code PUBLISHED} at once,
	 * without the green tick. Its flat number is {@code OPEN} when a tenant added it, {@code MASKED} like any listing
	 * when the owner did (D-57). {@code owner} is the caller when they own it, else the unclaimed-homes account; {@code proposer} files any new area, society, tower or wing as a proposal (D-109).
	 */
	@Transactional
	public Property newHome(CreatePropertyRequest request, User owner, User proposer, boolean pendingCity) {
		Property property = build(request, owner, proposer, pendingCity, true);
		property.setStatus(PropertyStatus.PUBLISHED);
		property.setUnitLabelVisibility(UnclaimedLandlord.is(owner) ? FieldVisibility.OPEN : FieldVisibility.MASKED);
		return property;
	}
	
	/** A public home for {@code POST /me/homes} with an existing {@code propertyId} ("Is it this one?"). */
	public Property publicHome(UUID publicId) {
		return properties.findByPublicIdAndDeletedAtIsNull(publicId)
			.filter(p -> p.getStatus() == PropertyStatus.PUBLISHED)
			.orElseThrow(() -> ApiException.notFound("Property"));
	}
	
	/** The property page of {@code property} as {@code caller} may see it. */
	public PropertyDetail detailOf(Property property, AuthenticatedUser caller) {
		return detail(property, caller);
	}
	
	/**
	 * {@code GET /me/homes/matches} (D-139): published homes in the same city, PIN code and society (by id, or by name
	 * when the society is typed as free text). A home whose flat number is {@code OPEN} must also have the flat
	 * number typed; a {@code MASKED} one matches on the society alone, so its flat number can never be probed.
	 * Shown as "Is it this one?" before a duplicate is added. At most 10.
	 */
	public List<PropertyCard> matches(String city, String pincode, UUID societyId, String societyName, String unit) {
		String unitKey = matchKey(unit);
		String societyKey = matchKey(societyName);
		if (unitKey.isEmpty()) {
			return List.of();
		}
		List<Property> found = properties.findByCityCodeAndPincodeAndStatusAndDeletedAtIsNull(city.trim(),
				pincode.trim(), PropertyStatus.PUBLISHED).stream()
			.filter(p -> p.getUnitLabelVisibility() == FieldVisibility.OPEN
				? unitKey.equals(matchKey(p.getUnitLabel())) && (societyId == null && societyKey.isEmpty()
					|| sameSociety(p, societyId, societyKey))
				: sameSociety(p, societyId, societyKey))
			.limit(10)
			.toList();
		Map<Long, RatingAggregate> ratings = aggregates(RatingSubjectType.PROPERTY,
			found.stream().map(Property::getId).toList());
		return found.stream().map(p -> card(p, ratings.get(p.getId()))).toList();
	}
	
	private static boolean sameSociety(Property p, UUID societyId, String societyKey) {
		if (societyId != null) {
			return p.getSociety() != null && societyId.equals(p.getSociety().getPublicId());
		}
		return !societyKey.isEmpty() && (societyKey.equals(matchKey(p.getBuildingName()))
			|| p.getSociety() != null && societyKey.equals(matchKey(p.getSociety().getName())));
	}
	
	/** Lower case, letters and digits only: "Flat C-1204" and "c 1204" match. */
	private static String matchKey(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "").replace("flat", "");
	}
	
	/**
	 * A new property from the request, owned by {@code owner}; {@code proposer} files any location proposals. A home
	 * in a pending city (D-143) has no hierarchy yet: it is placed by its free-text locality only. A home added by its
	 * tenant or owner ({@code addedHome}) may use a PIN code its area does not list (a warning in the form).
	 */
	private Property build(CreatePropertyRequest request, User owner, User proposer, boolean pendingCity,
		boolean addedHome) {
		if (request.homeSize() == HomeSize.OTHER && blankToNull(request.homeSizeOther()) == null) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("homeSizeOther", "required",
				"Type the size, e.g. 2.5 BHK.")));
		}
		String city = request.city().trim();
		String pincode = request.pincode().trim();
		if (pendingCity) {
			cityService.requirePincode(city, pincode);
			if (request.location() != null) {
				throw ApiException.validation(List.of(new ApiProblem.FieldError("home.location", "not_allowed",
					"A new city has no areas yet; type the locality instead.")));
			}
		} else {
			requireLocation(city, pincode);
		}
		Property property = new Property();
		property.setLandlord(owner);
		property.setCity(cities.getReferenceById(city));
		if (request.location() != null) {
			place(property, locations.resolve(city, pincode, request.location(), proposer, addedHome));
		} else {
			property.setLocality(requireLocality(request.locality()));
		}
		property.setBuildingName(blankToNull(request.buildingName()));
		property.setStreet(blankToNull(request.street()));
		property.setUnitLabel(blankToNull(request.unitLabel()));
		property.setPincode(pincode);
		property.setPropertyType(request.propertyType());
		property.setHomeSize(request.homeSize() != null ? request.homeSize()
			: HomeSize.fromBedrooms(request.propertyType(), request.bedrooms()));
		property.setHomeSizeOther(request.homeSize() == HomeSize.OTHER ? blankToNull(request.homeSizeOther()) : null);
		property.setBedrooms(request.bedrooms() != null || request.homeSize() == null ? request.bedrooms()
			: request.homeSize().bedrooms());
		property.setBathrooms(request.bathrooms());
		property.setAreaSqft(request.areaSqft());
		property.setFurnishing(request.furnishing());
		property.setParking(request.parking());
		property.setDescription(blankToNull(request.description()));
		return property;
	}
	
	/** {@code PATCH /properties/{id}}: owner edits; location locked outside DRAFT / REJECTED (D-92). */
	@Transactional
	public PropertyDetail update(UUID publicId, UpdatePropertyRequest request, AuthenticatedUser caller) {
		Property property = owned(publicId, caller);
		boolean moved = applyLocation(property, request);
		if (request.location() != null) {
			if (!property.getStatus().locationEditable()) {
				throw locationLocked(property, List.of("location"));
			}
			place(property, locations.resolve(property.getCity().getCode(), property.getPincode(), request.location(),
				currentUser(caller)));
		} else if (moved && property.getArea() != null) {
			// a new PIN code or city must still fit the current place (D-108, D-110)
			place(property, locations.resolve(property.getCity().getCode(), property.getPincode(),
				currentPlace(property), currentUser(caller)));
		}
		if (request.unitLabel() != null) {
			property.setUnitLabel(blankToNull(request.unitLabel()));
		}
		if (request.propertyType() != null) {
			property.setPropertyType(request.propertyType());
			if (property.getHomeSize() != HomeSize.OTHER
				&& (request.propertyType() == PropertyType.STUDIO || property.getHomeSize() == HomeSize.STUDIO)) {
				property.setHomeSize(HomeSize.fromBedrooms(request.propertyType(), property.getBedrooms()));
			}
		}
		if (request.bedrooms() != null) {
			property.setBedrooms(request.bedrooms());
			if (property.getHomeSize() != HomeSize.OTHER) {
				property.setHomeSize(HomeSize.fromBedrooms(property.getPropertyType(), request.bedrooms()));
			}
		}
		if (request.bathrooms() != null) {
			property.setBathrooms(request.bathrooms());
		}
		if (request.areaSqft() != null) {
			property.setAreaSqft(request.areaSqft());
		}
		if (request.furnishing() != null) {
			property.setFurnishing(request.furnishing());
		}
		if (request.parking() != null) {
			property.setParking(request.parking());
		}
		if (request.description() != null) {
			property.setDescription(blankToNull(request.description()));
		}
		if (request.status() != null && request.status() != property.getStatus()) {
			if (request.status() != PropertyStatus.ARCHIVED) {
				throw ApiException.validation(List.of(new ApiProblem.FieldError("status", "not_allowed",
					"Only ARCHIVED can be set here; use POST /properties/{id}/submit to send a listing for review.")));
			}
			archive(property);
		}
		return detail(properties.saveAndFlush(property), caller);
	}
	
	/**
	 * {@code POST /properties/{id}/submit}: {@code DRAFT} or {@code REJECTED} → {@code PENDING_REVIEW} (D-92).
	 * The landlord approval gate (D-91, PLAN B11) and the property-proof document check (B12) are added by
	 * those PRs; publishing is the admin decision of B13.
	 */
	@Transactional
	public PropertyDetail submit(UUID publicId, AuthenticatedUser caller) {
		Property property = owned(publicId, caller);
		if (!PropertyStatus.SUBMITTABLE.contains(property.getStatus())) {
			throw ApiException.conflict("invalid_transition",
				"A " + property.getStatus() + " listing cannot be submitted for review.");
		}
		property.setStatus(PropertyStatus.PENDING_REVIEW);
		return detail(properties.saveAndFlush(property), caller);
	}
	
	/**
	 * {@code DELETE /properties/{id}}: a published listing is archived, so its history stays readable by link
	 * (D-21); one that was never public is removed (soft delete). Repeating the call on an archived listing is a
	 * no-op.
	 */
	@Transactional
	public void delete(UUID publicId, AuthenticatedUser caller) {
		Property property = owned(publicId, caller);
		if (property.getStatus() == PropertyStatus.ARCHIVED) {
			return;
		}
		if (property.getStatus() == PropertyStatus.PUBLISHED) {
			property.setStatus(PropertyStatus.ARCHIVED);
		} else {
			property.setDeletedAt(TimestampedEntity.now());
		}
		properties.save(property);
	}
	
	/**
	 * Whether the user added this home (D-156): the tenant who added it through "Add your home", or the owner of a home
	 * no tenant added. Only they may change or delete it.
	 */
	public static boolean createdBy(Property property, UUID userId) {
		User creator = property.getAddedBy() != null ? property.getAddedBy() : property.getLandlord();
		return !UnclaimedLandlord.is(creator) && creator.getPublicId().equals(userId);
	}
	
	/**
	 * Applies {@code PATCH /me/homes/{id}} to a home its creator is changing (D-156) and returns the changed fields; the
	 * caller checks the creator and the role (D-163), resets the green tick and keeps the history. The add-home rules
	 * hold: the PIN code fits the city (a hard error, D-84) and may leave its area (only a warning there); a standalone
	 * apartment is named by its building; size "Other" needs its words. With {@code addressLocked} (other people have
	 * records on the home) only the size may change.
	 */
	@Transactional
	public EditHistory.Changes editHome(Property property, UpdateHomeRequest request, User me, boolean addressLocked) {
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		if (request.homeSize() == HomeSize.OTHER && blankToNull(request.homeSizeOther()) == null) {
			errors.add(new ApiProblem.FieldError("homeSizeOther", "required", "Type the size, e.g. 2.5 BHK."));
		}
		LocationInput place = request.location();
		boolean inSociety = place != null ? place.societyId() != null || place.newSociety() != null
			: property.getSociety() != null;
		if (property.getPropertyType() == PropertyType.APARTMENT && !inSociety
			&& blankToNull(request.buildingName()) == null) {
			errors.add(new ApiProblem.FieldError("buildingName", "required", "Enter the building name."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		List<String> before = address(property);
		String sizeBefore = sizeOf(property);
		String city = property.getCity().getCode();
		String pincode = request.pincode().trim();
		Placement placement = null;
		if (!property.getCity().isLive()) {
			// A home in a pending city (D-143) is placed by its locality only until the city has areas.
			cityService.requirePincode(city, pincode);
			if (place != null) {
				throw ApiException.validation(List.of(new ApiProblem.FieldError("location", "not_allowed",
					"A new city has no areas yet.")));
			}
		} else if (place != null || property.getArea() != null) {
			placement = locations.resolve(city, pincode, place != null ? place : currentPlace(property), me, true);
		} else {
			requireLocation(city, pincode);
		}
		List<String> after = placement == null
			? Arrays.asList(before.get(0), before.get(1), before.get(2), before.get(3), null, null, null, null)
			: placeNames(placement);
		after.set(4, blankToNull(request.buildingName()));
		after.set(5, blankToNull(request.street()));
		after.set(6, blankToNull(request.unitLabel()));
		after.set(7, pincode);
		List<String> locked = new ArrayList<>();
		EditHistory.Changes changes = new EditHistory.Changes();
		for (int i = 0; i < ADDRESS_FIELDS.size(); i++) {
			changes.field(ADDRESS_FIELDS.get(i), before.get(i), after.get(i));
			if (!Objects.equals(before.get(i), after.get(i))) {
				locked.add(ADDRESS_FIELDS.get(i));
			}
		}
		// Checked before anything changes on the row.
		if (addressLocked && !locked.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "location_locked",
				"Other people have tenancies on this home, so its address cannot change; ask our team to correct it.",
				locked.stream().map(field -> new ApiProblem.FieldError(field, "locked",
					"Other people's records are on this home.")).toList());
		}
		if (placement != null) {
			place(property, placement);
		}
		property.setPincode(pincode);
		property.setBuildingName(blankToNull(request.buildingName()));
		property.setStreet(blankToNull(request.street()));
		property.setUnitLabel(blankToNull(request.unitLabel()));
		if (request.homeSize() != null || property.getHomeSize() != null) {
			property.setHomeSize(request.homeSize());
			property.setHomeSizeOther(request.homeSize() == HomeSize.OTHER ? blankToNull(request.homeSizeOther()) : null);
			if (request.homeSize() != null && request.homeSize().bedrooms() != null) {
				property.setBedrooms(request.homeSize().bedrooms());
			}
		}
		changes.field("homeSize", sizeBefore, sizeOf(property));
		return changes;
	}
	
	/** The address fields an owner may change, in the order of {@link #address}. */
	private static final List<String> ADDRESS_FIELDS = List.of("area", "society", "tower", "wing", "buildingName",
		"street", "unitLabel", "pincode");
	
	private static List<String> address(Property p) {
		return Arrays.asList(p.getArea() == null ? p.getLocality() : p.getArea().getName(),
			p.getSociety() == null ? null : p.getSociety().getName(), p.getTower() == null ? null : p.getTower().getName(),
			p.getWing() == null ? null : p.getWing().getName(), p.getBuildingName(), p.getStreet(), p.getUnitLabel(),
			p.getPincode());
	}
	
	/** The place part of {@link #address} for a new placement; the other four are filled in by the caller. */
	private static List<String> placeNames(Placement placement) {
		return Arrays.asList(placement.area().getName(),
			placement.society() == null ? null : placement.society().getName(),
			placement.tower() == null ? null : placement.tower().getName(),
			placement.wing() == null ? null : placement.wing().getName(), null, null, null, null);
	}
	
	private static String sizeOf(Property p) {
		if (p.getHomeSize() == null) {
			return null;
		}
		return p.getHomeSize() == HomeSize.OTHER ? "OTHER: " + p.getHomeSizeOther() : p.getHomeSize().name();
	}
	
	// -------------------------------------------------------------- helpers
	
	private void archive(Property property) {
		if (property.getStatus() != PropertyStatus.PUBLISHED) {
			throw ApiException.conflict("invalid_transition",
				"Only a PUBLISHED listing can be archived; delete a " + property.getStatus() + " one instead.");
		}
		property.setStatus(PropertyStatus.ARCHIVED);
	}
	
	/**
	 * Applies the free-text location fields; returns true when the city or PIN code changed. With a
	 * {@code location} the locality comes from its area, so {@code locality} is ignored.
	 */
	private boolean applyLocation(Property property, UpdatePropertyRequest request) {
		String city = trimOrNull(request.city());
		String locality = request.locality() == null || request.location() != null ? null
			: requireLocality(request.locality());
		String pincode = trimOrNull(request.pincode());
		String buildingName = request.buildingName() == null ? null : blankToNull(request.buildingName());
		String street = request.street() == null ? null : blankToNull(request.street());
		List<String> changed = new ArrayList<>();
		if (city != null && !city.equals(property.getCity().getCode())) {
			changed.add("city");
		}
		if (locality != null && !locality.equals(property.getLocality())) {
			changed.add("locality");
		}
		if (pincode != null && !pincode.equals(property.getPincode())) {
			changed.add("pincode");
		}
		if (request.buildingName() != null && !Objects.equals(buildingName, property.getBuildingName())) {
			changed.add("buildingName");
		}
		if (request.street() != null && !Objects.equals(street, property.getStreet())) {
			changed.add("street");
		}
		if (changed.isEmpty()) {
			return false;
		}
		if (!property.getStatus().locationEditable()) {
			throw locationLocked(property, changed);
		}
		boolean moved = changed.contains("city") || changed.contains("pincode");
		if (moved) {
			String newCity = city != null ? city : property.getCity().getCode();
			String newPincode = pincode != null ? pincode : property.getPincode();
			requireLocation(newCity, newPincode);
			property.setCity(cities.getReferenceById(newCity));
			property.setPincode(newPincode);
		}
		if (locality != null) {
			property.setLocality(locality);
		}
		if (request.buildingName() != null) {
			property.setBuildingName(buildingName);
		}
		if (request.street() != null) {
			property.setStreet(street);
		}
		return moved;
	}
	
	private static ApiException locationLocked(Property property, List<String> fields) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "location_locked",
			"The location of a " + property.getStatus() + " listing cannot change; ask an admin.",
			fields.stream().map(field -> new ApiProblem.FieldError(field, "locked", "Locked after submit.")).toList());
	}
	
	/** Puts the home on a checked place; the locality is the area's name (D-110). */
	private static void place(Property property, Placement placement) {
		property.setArea(placement.area());
		property.setSociety(placement.society());
		property.setTower(placement.tower());
		property.setWing(placement.wing());
		property.setLocality(placement.area().getName());
	}
	
	private static LocationInput currentPlace(Property property) {
		return LocationInput.of(property.getArea().getPublicId(),
			property.getSociety() == null ? null : property.getSociety().getPublicId(),
			property.getTower() == null ? null : property.getTower().getPublicId(),
			property.getWing() == null ? null : property.getWing().getPublicId());
	}
	
	private void requireLocation(String city, String pincode) {
		cityService.requireLive(city);
		cityService.requirePincode(city, pincode);
	}
	
	/** Readable by the caller, else 404 (no existence leak, D-60). */
	private Property visible(UUID publicId, AuthenticatedUser caller) {
		return properties.findByPublicIdAndDeletedAtIsNull(publicId)
			.filter(p -> p.getStatus().isPublic() || isOwner(p, caller) || isAdmin(caller))
			.orElseThrow(() -> ApiException.notFound("Property"));
	}
	
	/** Visible and owned by the caller: 404 when not visible, 403 when visible but someone else's. */
	private Property owned(UUID publicId, AuthenticatedUser caller) {
		Property property = visible(publicId, caller);
		if (!isOwner(property, caller)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "Only the owner can change this property.");
		}
		return property;
	}
	
	private User currentUser(AuthenticatedUser caller) {
		return users.findByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue."));
	}
	
	private static boolean isOwner(Property property, AuthenticatedUser caller) {
		return caller != null && property.getLandlord().getPublicId().equals(caller.publicId());
	}
	
	private static boolean isAdmin(AuthenticatedUser caller) {
		return caller != null && caller.hasRole(Role.ADMIN);
	}
	
	private PropertyDetail detail(Property property, AuthenticatedUser caller) {
		RatingSummary rating = RatingSummary.of(aggregate(RatingSubjectType.PROPERTY, property.getId()));
		RatingSummary landlordRating = UnclaimedLandlord.is(property.getLandlord()) ? null : RatingSummary.of(
			aggregate(RatingSubjectType.LANDLORD, property.getLandlord().getId()));
		boolean landlordVerified = !UnclaimedLandlord.is(property.getLandlord())
			&& greenTicks.has(property.getLandlord(), Role.LANDLORD);
		PropertyDetail base = detail(property, rating, landlordRating, landlordVerified, caller != null,
			isOwner(property, caller) || isAdmin(caller), caller != null && createdBy(property, caller.publicId()));
		// The owner contact a tenant entered stays private to them and admins (D-140).
		boolean adder = caller != null && property.getAddedBy() != null
			&& property.getAddedBy().getPublicId().equals(caller.publicId());
		return adder || isAdmin(caller) ? base.withOwnerContact(OwnerContact.of(property)) : base;
	}
	
	/**
	 * D-56 / D-57: the unit label only for the owner and admins ({@code privileged}); the landlord profile link
	 * ({@code landlord.id}) only for signed-in callers (D-55).
	 */
	private static PropertyDetail detail(Property p, RatingSummary rating, RatingSummary landlordRating,
		boolean landlordVerified, boolean signedIn, boolean privileged, boolean editable) {
		User landlord = p.getLandlord();
		// An unclaimed home (D-139) has no landlord profile to link to or rate.
		LandlordSummary owner = UnclaimedLandlord.is(landlord)
			? new LandlordSummary(null, UnclaimedLandlord.PUBLIC_NAME, null, false)
			: new LandlordSummary(signedIn ? landlord.getPublicId() : null,
				PublicNames.of(landlord), landlordRating, landlordVerified);
		return new PropertyDetail(p.getPublicId(), CityRef.of(p.getCity()), p.getLocality(), p.getBuildingName(),
			p.getStreet(), p.getPincode(),
			privileged || p.getUnitLabelVisibility() == FieldVisibility.OPEN ? p.getUnitLabel() : null,
			p.getUnitLabelVisibility() == FieldVisibility.OPEN,
			p.getPropertyType(), p.getBedrooms(), p.getHomeSize(), p.getHomeSizeOther(), p.getBathrooms(), p.getAreaSqft(),
			p.getFurnishing(), p.getParking(),
			p.getDescription(), p.getStatus(), p.isDemo(), rating, owner, p.getCreatedAt(), p.getUpdatedAt(),
			PropertyLocation.of(p.getArea(), p.getSociety(), p.getTower(), p.getWing()), p.getAddedBy() != null,
			VerificationState.of(p.getVerificationStatus()), null, editable);
	}
	
	private static PropertyCard card(Property p, RatingAggregate rating) {
		return new PropertyCard(p.getPublicId(), CityRef.of(p.getCity()), p.getLocality(), p.getBuildingName(),
			p.getStreet(), p.getPropertyType(), p.getBedrooms(), p.getHomeSize(), p.getHomeSizeOther(), p.getBathrooms(),
			p.getAreaSqft(), p.getFurnishing(),
			p.getParking(), p.isDemo(), RatingSummary.of(rating), landlordName(p.getLandlord()), locationPending(p),
			VerificationState.of(p.getVerificationStatus()));
	}
	
	/** True when any level of the home's place is a {@code PENDING} proposal (see {@link PropertyCard}). */
	private static boolean locationPending(Property p) {
		return pending(p.getArea() == null ? null : p.getArea().getStatus())
			|| pending(p.getSociety() == null ? null : p.getSociety().getStatus())
			|| pending(p.getTower() == null ? null : p.getTower().getStatus())
			|| pending(p.getWing() == null ? null : p.getWing().getStatus());
	}
	
	private static boolean pending(LocationStatus status) {
		return status == LocationStatus.PENDING;
	}
	
	private static String landlordName(User landlord) {
		return UnclaimedLandlord.is(landlord) ? UnclaimedLandlord.PUBLIC_NAME
			: PublicNames.of(landlord);
	}
	
	private RatingAggregate aggregate(RatingSubjectType type, Long subjectId) {
		return aggregates.findById(new RatingAggregateId(type, subjectId)).orElse(null);
	}
	
	private Map<Long, RatingAggregate> aggregates(RatingSubjectType type, Collection<Long> subjectIds) {
		if (subjectIds.isEmpty()) {
			return Map.of();
		}
		List<RatingAggregateId> keys = subjectIds.stream().map(id -> new RatingAggregateId(type, id)).toList();
		return aggregates.findAllById(keys).stream()
			.collect(Collectors.toMap(a -> a.getId().subjectId(), Function.identity()));
	}
	
	private static String requireLocality(String locality) {
		if (locality == null || locality.isBlank()) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("locality", "required",
				"Pick an area (location.areaId) or give a locality.")));
		}
		String trimmed = locality.trim().replaceAll("\\s+", " ");
		if (trimmed.length() < 2) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("locality", "size",
				"Must be 2 to 120 characters.")));
		}
		return trimmed;
	}
	
	private static String trimOrNull(String value) {
		return value == null ? null : value.trim();
	}
	
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
	
}
