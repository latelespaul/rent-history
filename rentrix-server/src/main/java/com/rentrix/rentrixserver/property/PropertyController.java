package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.property.dto.CreatePropertyRequest;
import com.rentrix.rentrixserver.property.dto.GroupedPage;
import com.rentrix.rentrixserver.property.dto.PropertyCard;
import com.rentrix.rentrixserver.property.dto.PropertyDetail;
import com.rentrix.rentrixserver.property.dto.PropertyLimits;
import com.rentrix.rentrixserver.property.dto.SocietyGroup;
import com.rentrix.rentrixserver.property.dto.UpdatePropertyRequest;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Property endpoints (§12.2). {@code GET /properties} and {@code GET /properties/{id}} are public (fields per
 * D-56); writes need a signed-in owner, creating needs the {@code LANDLORD} role (D-21).
 */
@RestController
@RequiredArgsConstructor
public class PropertyController {
	
	/** Sort allow-list (D-59); each name maps to itself and {@link PropertySearchQuery} owns the SQL. */
	static final Map<String, String> SEARCH_SORTS = Map.of(
		PropertySearchQuery.TOP_RATED, PropertySearchQuery.TOP_RATED,
		PropertySearchQuery.MOST_REVIEWED, PropertySearchQuery.MOST_REVIEWED,
		PropertySearchQuery.NEWEST, PropertySearchQuery.NEWEST);
	static final Sort DEFAULT_SEARCH_SORT = Sort.by(Sort.Direction.DESC, PropertySearchQuery.TOP_RATED);
	
	static final Map<String, String> MINE_SORTS = Map.of("newest", "createdAt", "updated", "updatedAt");
	static final Sort DEFAULT_MINE_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");
	
	static final int MAX_TEXT = 100;
	private static final BigDecimal MIN_RATING = BigDecimal.ONE;
	private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);
	
	private final PropertyService service;
	
	/**
	 * {@code GET /properties?city=&area=&society=&tower=&standalone=&homeSize=&locality=&type=&bedrooms=&minRating=&q=&page=
	 * &size=&sort=}: published homes.
	 * {@code city} is a live city code (else {@code 422 city_not_live}); without it every live city is searched.
	 * {@code locality} is a prefix, {@code q} matches locality, building or street, {@code minRating} (1–5)
	 * keeps homes whose shown average (one decimal) reaches it, i.e. 3 or more reviews (D-52). A {@code sort}
	 * name without a direction sorts descending. {@code area}, {@code society} and {@code tower} are
	 * {@code public_id}s (D-112); {@code standalone=true} keeps homes outside any society, {@code false} those in
	 * one.
	 */
	@GetMapping("/properties")
	public PageResponse<PropertyCard> search(@AuthenticationPrincipal AuthenticatedUser caller,
		@RequestParam(required = false) String city,
		@RequestParam(required = false) String locality,
		@RequestParam(required = false) List<String> type,
		@RequestParam(required = false) Integer bedrooms,
		@RequestParam(required = false) BigDecimal minRating,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) UUID area,
		@RequestParam(required = false) UUID society,
		@RequestParam(required = false) UUID tower,
		@RequestParam(required = false) Boolean standalone,
		@RequestParam(required = false) List<String> homeSize,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size,
		@RequestParam(required = false) String sort) {
		PropertySearchCriteria criteria = criteria(city, locality, type, bedrooms, minRating, q, society, tower,
			standalone, homeSize);
		return service.search(criteria, new PropertyService.LocationFilter(area, society, tower, standalone), caller,
			pageable(page, size, sort));
	}
	
	/**
	 * {@code GET /properties/grouped}, same parameters as {@link #search} (D-166): a society with 2+ matching homes
	 * is one {@code SOCIETY} row, every other home a {@code HOME} row; pages run over rows and {@code totalHomes}
	 * counts the homes.
	 */
	@GetMapping("/properties/grouped")
	public GroupedPage searchGrouped(@AuthenticationPrincipal AuthenticatedUser caller,
		@RequestParam(required = false) String city,
		@RequestParam(required = false) String locality,
		@RequestParam(required = false) List<String> type,
		@RequestParam(required = false) Integer bedrooms,
		@RequestParam(required = false) BigDecimal minRating,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) UUID area,
		@RequestParam(required = false) UUID society,
		@RequestParam(required = false) UUID tower,
		@RequestParam(required = false) Boolean standalone,
		@RequestParam(required = false) List<String> homeSize,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size,
		@RequestParam(required = false) String sort) {
		PropertySearchCriteria criteria = criteria(city, locality, type, bedrooms, minRating, q, society, tower,
			standalone, homeSize);
		return service.searchGrouped(criteria, new PropertyService.LocationFilter(area, society, tower, standalone),
			caller, pageable(page, size, sort));
	}
	
	/** {@code GET /societies/{id}/summary} (D-166): the society page's flats, reviews and rent range. */
	@GetMapping("/societies/{id}/summary")
	public SocietyGroup societySummary(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return service.societySummary(id, caller);
	}
	
	/** The validated filters shared by both searches; location ids stay for the service to resolve. */
	private static PropertySearchCriteria criteria(String city, String locality, List<String> type,
		Integer bedrooms, BigDecimal minRating, String q, UUID society, UUID tower, Boolean standalone,
		List<String> homeSize) {
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		String localityKey = text("locality", locality, 120, errors);
		String textKey = text("q", q, MAX_TEXT, errors);
		List<PropertyType> propertyTypes = choices("type", type, PropertyType.class, errors);
		if (bedrooms != null && (bedrooms < 0 || bedrooms > PropertyLimits.MAX_ROOMS)) {
			errors.add(new ApiProblem.FieldError("bedrooms", "range",
				"Must be between 0 and " + PropertyLimits.MAX_ROOMS + "."));
		}
		List<HomeSize> sizeFilter = choices("homeSize", homeSize, HomeSize.class, errors);
		if (minRating != null && (minRating.compareTo(MIN_RATING) < 0 || minRating.compareTo(MAX_RATING) > 0)) {
			errors.add(new ApiProblem.FieldError("minRating", "range", "Must be between 1 and 5."));
		}
		if (Boolean.TRUE.equals(standalone) && (society != null || tower != null)) {
			errors.add(new ApiProblem.FieldError("standalone", "conflict",
				"Standalone homes are outside any society; drop society or tower."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		String cityCode = city == null || city.isBlank() ? null : city.trim().toUpperCase(Locale.ROOT);
		return new PropertySearchCriteria(cityCode, localityKey, propertyTypes, bedrooms, minRating, textKey, null,
			null, null, null, sizeFilter, null);
	}
	
	private static Pageable pageable(Integer page, Integer size, String sort) {
		String searchSort = sort == null || sort.isBlank() || sort.contains(",") ? sort : sort + ",desc";
		return PageRequests.of(page, size, searchSort, SEARCH_SORTS, DEFAULT_SEARCH_SORT);
	}
	
	@GetMapping("/properties/{id}")
	public PropertyDetail get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return service.get(id, caller);
	}
	
	@PostMapping("/properties")
	@PreAuthorize("hasRole('LANDLORD')")
	public ResponseEntity<PropertyDetail> create(@Valid @RequestBody CreatePropertyRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		PropertyDetail created = service.create(request, caller);
		return ResponseEntity.created(URI.create("/api/v1/properties/" + created.id())).body(created);
	}
	
	@PatchMapping("/properties/{id}")
	public PropertyDetail update(@PathVariable UUID id, @Valid @RequestBody UpdatePropertyRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return service.update(id, request, caller);
	}
	
	@PostMapping("/properties/{id}/submit")
	public PropertyDetail submit(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return service.submit(id, caller);
	}
	
	@DeleteMapping("/properties/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		service.delete(id, caller);
		return ResponseEntity.noContent().build();
	}
	
	/** {@code GET /me/properties}: the caller's own listings, every state, with unit labels. */
	@GetMapping("/me/properties")
	public PageResponse<PropertyDetail> mine(@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return service.mine(caller, PageRequests.of(page, size, sort, MINE_SORTS, DEFAULT_MINE_SORT));
	}
	
	private static String text(String field, String value, int max, List<ApiProblem.FieldError> errors) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String key = Property.localityKey(value);
		if (key.length() > max) {
			errors.add(new ApiProblem.FieldError(field, "size", "At most " + max + " characters."));
			return null;
		}
		return key;
	}
	
	/**
	 * The values of a repeated ({@code type=A&type=B}) or comma-separated ({@code type=A,B}) filter, as enum
	 * constants without repeats; one bad value is a {@code not_allowed} error and a blank list is no filter.
	 */
	private static <E extends Enum<E>> List<E> choices(String field, List<String> values, Class<E> type,
		List<ApiProblem.FieldError> errors) {
		List<E> chosen = new ArrayList<>();
		if (values == null) {
			return chosen;
		}
		for (String raw : values) {
			for (String part : raw.split(",")) {
				if (part.isBlank()) {
					continue;
				}
				try {
					E value = Enum.valueOf(type, part.trim().toUpperCase(Locale.ROOT));
					if (!chosen.contains(value)) {
						chosen.add(value);
					}
				} catch (IllegalArgumentException e) {
					errors.add(new ApiProblem.FieldError(field, "not_allowed",
						"Use one of " + List.of(type.getEnumConstants()) + "."));
					return chosen;
				}
			}
		}
		return chosen;
	}
	
}
