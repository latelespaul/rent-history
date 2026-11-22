package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.persistence.TimestampedEntity;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.location.Area;
import com.rentrix.rentrixserver.location.LocationService;
import com.rentrix.rentrixserver.location.Society;
import com.rentrix.rentrixserver.location.SocietyTower;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.property.dto.CreatePropertyRequest;
import com.rentrix.rentrixserver.property.dto.LandlordSummary;
import com.rentrix.rentrixserver.property.dto.PropertyCard;
import com.rentrix.rentrixserver.property.dto.PropertyDetail;
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
import java.util.Collection;
import java.util.List;
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
		return PageResponse.of(page, p -> detail(p, RatingSummary.of(ratings.get(p.getId())), landlordRating, true, true));
	}
	
	/**
	 * Resolves the location filters to ids. Unknown ids, and someone else's {@code PENDING} proposal, are
	 * {@code 422} on the parameter; a tower outside the society, a society outside the area, or any of them outside
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
			c.text(), areaId, societyId, towerId, standalone);
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
		String city = request.city().trim();
		String pincode = request.pincode().trim();
		requireLocation(city, pincode);
		Property property = new Property();
		property.setLandlord(landlord);
		property.setCity(cities.getReferenceById(city));
		property.setLocality(requireLocality(request.locality()));
		property.setBuildingName(blankToNull(request.buildingName()));
		property.setStreet(blankToNull(request.street()));
		property.setUnitLabel(blankToNull(request.unitLabel()));
		property.setPincode(pincode);
		property.setPropertyType(request.propertyType());
		property.setBedrooms(request.bedrooms());
		property.setBathrooms(request.bathrooms());
		property.setAreaSqft(request.areaSqft());
		property.setFurnishing(request.furnishing());
		property.setParking(request.parking());
		property.setDescription(blankToNull(request.description()));
		property.setStatus(PropertyStatus.DRAFT);
		Property saved = properties.saveAndFlush(property);
		return detail(saved, caller);
	}
	
	/** {@code PATCH /properties/{id}}: owner edits; location locked outside DRAFT / REJECTED (D-92). */
	@Transactional
	public PropertyDetail update(UUID publicId, UpdatePropertyRequest request, AuthenticatedUser caller) {
		Property property = owned(publicId, caller);
		applyLocation(property, request);
		if (request.unitLabel() != null) {
			property.setUnitLabel(blankToNull(request.unitLabel()));
		}
		if (request.propertyType() != null) {
			property.setPropertyType(request.propertyType());
		}
		if (request.bedrooms() != null) {
			property.setBedrooms(request.bedrooms());
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
	
	// -------------------------------------------------------------- helpers
	
	private void archive(Property property) {
		if (property.getStatus() != PropertyStatus.PUBLISHED) {
			throw ApiException.conflict("invalid_transition",
				"Only a PUBLISHED listing can be archived; delete a " + property.getStatus() + " one instead.");
		}
		property.setStatus(PropertyStatus.ARCHIVED);
	}
	
	private void applyLocation(Property property, UpdatePropertyRequest request) {
		String city = trimOrNull(request.city());
		String locality = request.locality() == null ? null : requireLocality(request.locality());
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
			return;
		}
		if (!property.getStatus().locationEditable()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "location_locked",
				"The location of a " + property.getStatus() + " listing cannot change; ask an admin.",
				changed.stream().map(field -> new ApiProblem.FieldError(field, "locked", "Locked after submit."))
					.toList());
		}
		if (changed.contains("city") || changed.contains("pincode")) {
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
		RatingSummary landlordRating = RatingSummary.of(
			aggregate(RatingSubjectType.LANDLORD, property.getLandlord().getId()));
		return detail(property, rating, landlordRating, caller != null, isOwner(property, caller) || isAdmin(caller));
	}
	
	/**
	 * D-56 / D-57: the unit label only for the owner and admins ({@code privileged}); the landlord profile link
	 * ({@code landlord.id}) only for signed-in callers (D-55).
	 */
	private static PropertyDetail detail(Property p, RatingSummary rating, RatingSummary landlordRating,
		boolean signedIn, boolean privileged) {
		User landlord = p.getLandlord();
		LandlordSummary owner = new LandlordSummary(signedIn ? landlord.getPublicId() : null,
			PublicNames.shortName(landlord.getDisplayName()), landlordRating);
		return new PropertyDetail(p.getPublicId(), CityRef.of(p.getCity()), p.getLocality(), p.getBuildingName(),
			p.getStreet(), p.getPincode(), privileged ? p.getUnitLabel() : null, p.getPropertyType(), p.getBedrooms(),
			p.getBathrooms(), p.getAreaSqft(), p.getFurnishing(), p.getParking(), p.getDescription(), p.getStatus(),
			p.isDemo(), rating, owner, p.getCreatedAt(), p.getUpdatedAt());
	}
	
	private static PropertyCard card(Property p, RatingAggregate rating) {
		return new PropertyCard(p.getPublicId(), CityRef.of(p.getCity()), p.getLocality(), p.getBuildingName(),
			p.getStreet(), p.getPropertyType(), p.getBedrooms(), p.getBathrooms(), p.getAreaSqft(), p.getFurnishing(),
			p.getParking(), p.isDemo(), RatingSummary.of(rating), PublicNames.shortName(p.getLandlord().getDisplayName()));
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
