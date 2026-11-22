package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.city.CityRepository;
import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.location.dto.AreaCard;
import com.rentrix.rentrixserver.location.dto.AreaDetail;
import com.rentrix.rentrixserver.location.dto.AreaRef;
import com.rentrix.rentrixserver.location.dto.SocietyCard;
import com.rentrix.rentrixserver.location.dto.SocietyDetail;
import com.rentrix.rentrixserver.location.dto.TowerView;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The location hierarchy City → Area → Society → Tower → Wing → Unit (§5.19, D-107 – D-110): the public reads
 * behind {@code /areas} and {@code /societies}, the lookups behind the {@code GET /properties} location filters,
 * and {@link #resolve}, which checks where a property goes and saves inline proposals as {@code PENDING} rows.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {
	
	static final int MAX_AREA_NAME = 120;
	static final int MAX_SOCIETY_NAME = 160;
	static final int MAX_TOWER_NAME = 60;
	static final int MAX_DEVELOPER = 120;
	static final int MAX_STREET = 200;
	static final int MAX_QUERY = 100;
	
	private static final Pattern PINCODE = Pattern.compile("\\d{6}");
	private static final char LIKE_ESCAPE = '!';
	/** Prefix of every field name in {@link #resolve} errors, matching the request's {@code location} object. */
	private static final String LOCATION = "location.";
	
	private final AreaRepository areas;
	private final SocietyRepository societies;
	private final SocietyTowerRepository towers;
	private final SocietyWingRepository wings;
	private final CityRepository cities;
	private final CityService cityService;
	
	// ------------------------------------------------------------------ areas
	
	/**
	 * {@code GET /areas?city=&q=&pincode=} (D-108): areas of a live city (or of every live city), matched by a
	 * word prefix of the name or an alias, or by an exact PIN code through {@code area_pincodes}.
	 */
	public PageResponse<AreaCard> areas(String cityCode, String q, String pincode, AuthenticatedUser caller,
		Pageable pageable) {
		Viewer viewer = Viewer.of(caller);
		String query = query(q);
		String pin = pincodeFilter(pincode);
		if (cityCode != null) {
			cityService.requireLive(cityCode);
		}
		Specification<Area> spec = (root, cq, cb) -> {
			List<Predicate> where = new ArrayList<>();
			where.add(listed(root, cb, viewer));
			where.add(cityCode != null ? cb.equal(root.get("city").get("code"), cityCode)
				: cb.isTrue(root.join("city").get("live")));
			if (query != null) {
				where.add(cb.or(wordPrefix(cb, root.get("nameKey"), query),
					wordPrefix(cb, cb.lower(root.get("aliases")), query)));
			}
			if (pin != null) {
				Subquery<Long> withPin = cq.subquery(Long.class);
				Root<AreaPincode> ap = withPin.from(AreaPincode.class);
				withPin.select(ap.get("id").get("areaId")).where(cb.equal(ap.get("id").get("pincode"), pin));
				where.add(root.get("id").in(withPin));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
		Page<Area> page = areas.findAll(spec, stable(pageable));
		List<Long> ids = page.getContent().stream().map(Area::getId).toList();
		Map<Long, Long> homes = counts(ids.isEmpty() ? List.of() : areas.countPublishedHomes(ids));
		Map<Long, Long> societyCounts = counts(ids.isEmpty() ? List.of() : areas.countActiveSocieties(ids));
		return PageResponse.of(page, area -> new AreaCard(area.getPublicId(), CityRef.of(area.getCity()),
			area.getName(), area.aliasList(), area.pincodeList(), area.getStatus(),
			societyCounts.getOrDefault(area.getId(), 0L), homes.getOrDefault(area.getId(), 0L)));
	}
	
	/** {@code GET /areas/{id}}: the area page with its listed societies (D-112). */
	public AreaDetail area(UUID publicId, AuthenticatedUser caller) {
		Viewer viewer = Viewer.of(caller);
		Area area = readableArea(publicId, viewer);
		List<Long> id = List.of(area.getId());
		Specification<Society> inArea = (root, cq, cb) -> cb.and(cb.equal(root.get("area"), area),
			listed(root, cb, viewer));
		List<Society> listed = societies.findAll(inArea, Sort.by("nameKey", "id"));
		return new AreaDetail(area.getPublicId(), CityRef.of(area.getCity()), area.getName(), area.aliasList(),
			area.pincodeList(), area.getStatus(), counts(areas.countPublishedHomes(id)).getOrDefault(area.getId(), 0L),
			counts(areas.countStandaloneHomes(id)).getOrDefault(area.getId(), 0L), societyCards(listed));
	}
	
	// -------------------------------------------------------------- societies
	
	/** {@code GET /societies?city=&area=&q=&kind=}: societies matched by a word prefix of name or developer. */
	public PageResponse<SocietyCard> societies(String cityCode, UUID areaId, String q, SocietyKind kind,
		AuthenticatedUser caller, Pageable pageable) {
		Viewer viewer = Viewer.of(caller);
		String query = query(q);
		if (cityCode != null) {
			cityService.requireLive(cityCode);
		}
		Area area = areaId == null ? null : readableArea(areaId, viewer, "area");
		Specification<Society> spec = (root, cq, cb) -> {
			List<Predicate> where = new ArrayList<>();
			where.add(listed(root, cb, viewer));
			where.add(cityCode != null ? cb.equal(root.get("city").get("code"), cityCode)
				: cb.isTrue(root.join("city").get("live")));
			if (area != null) {
				where.add(cb.equal(root.get("area"), area));
			}
			if (kind != null) {
				where.add(cb.equal(root.get("kind"), kind));
			}
			if (query != null) {
				where.add(cb.or(wordPrefix(cb, root.get("nameKey"), query),
					wordPrefix(cb, cb.lower(root.get("developer")), query)));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
		Page<Society> page = societies.findAll(spec, stable(pageable));
		List<SocietyCard> cards = societyCards(page.getContent());
		return PageResponse.of(new PageImpl<>(cards, page.getPageable(), page.getTotalElements()), card -> card);
	}
	
	/** {@code GET /societies/{id}}: the society page, towers → wings with published-home counts (D-112). */
	public SocietyDetail society(UUID publicId, AuthenticatedUser caller) {
		Viewer viewer = Viewer.of(caller);
		Society society = readableSociety(publicId, viewer);
		List<TowerView> towerViews = towerViews(society, viewer);
		long homes = counts(societies.countPublishedHomes(List.of(society.getId())))
			.getOrDefault(society.getId(), 0L);
		return new SocietyDetail(society.getPublicId(), society.getName(), society.getDeveloper(), society.getKind(),
			AreaRef.of(society.getArea()), CityRef.of(society.getCity()), society.getStreet(), society.getStatus(),
			homes, towerViews);
	}
	
	/** {@code GET /societies/{id}/towers}: towers and wings for the property form pickers. */
	public List<TowerView> towers(UUID societyId, AuthenticatedUser caller) {
		Viewer viewer = Viewer.of(caller);
		return towerViews(readableSociety(societyId, viewer), viewer);
	}
	
	// ---------------------------------------------------------- property filters
	
	/** The area behind {@code GET /properties?area=}; unknown or someone else's proposal is 422 on {@code field}. */
	public Area areaFilter(UUID publicId, AuthenticatedUser caller, String field) {
		return readableArea(publicId, Viewer.of(caller), field);
	}
	
	public Society societyFilter(UUID publicId, AuthenticatedUser caller, String field) {
		return readableSociety(publicId, Viewer.of(caller), field);
	}
	
	public SocietyTower towerFilter(UUID publicId, AuthenticatedUser caller, String field) {
		Viewer viewer = Viewer.of(caller);
		return towers.findByPublicId(publicId)
			.filter(t -> viewer.reads(t.getStatus(), t.getCreatedBy()))
			.orElseThrow(() -> unknown(field, "tower"));
	}
	
	// ------------------------------------------------------------------ resolve
	
	/**
	 * Checks where a property goes and saves any inline proposals (D-108 – D-110). Rules:
	 * <ul>
	 * <li>the city is live and the property {@code pincode} starts with one of its prefixes (D-84);</li>
	 * <li>the area is required; a wing needs a tower and a tower needs a society
	 * ({@code 422 location_mismatch});</li>
	 * <li>each level is an existing row the actor may use ({@code ACTIVE}, or {@code PENDING} they proposed; admins
	 * any {@code PENDING}) or a proposal, never both; {@code HIDDEN} rows take no new homes;</li>
	 * <li>the wing belongs to the tower, the tower to the society, the society to the area and the area to the
	 * city ({@code 422 location_mismatch});</li>
	 * <li>the {@code pincode} is one of the area's PIN codes ({@code 422 pincode_not_in_area});</li>
	 * <li>a proposal's name is new under its parent, else {@code 409 location_exists} when the existing row is
	 * {@code ACTIVE} or {@code HIDDEN}; a {@code PENDING} row of that name is joined instead: the home goes on it,
	 * it keeps its proposer, and the same rule applies to the proposals below it (no duplicate rows).</li>
	 * </ul>
	 * Proposals by a landlord are saved {@code PENDING} with {@code created_by} = the actor, so only they and
	 * admins see them until listing approval; an admin's are {@code ACTIVE} (D-109). Call inside the transaction
	 * that saves the property.
	 */
	@Transactional
	public Placement resolve(String cityCode, String pincode, LocationInput input, User actor) {
		Objects.requireNonNull(actor, "actor");
		Viewer viewer = Viewer.of(actor);
		LocationInput in = input == null ? new LocationInput(null, null, null, null, null, null, null, null) : input;
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		oneOf(in.areaId(), in.newArea(), "areaId", "newArea", true, errors);
		oneOf(in.societyId(), in.newSociety(), "societyId", "newSociety", false, errors);
		oneOf(in.towerId(), in.newTower(), "towerId", "newTower", false, errors);
		oneOf(in.wingId(), in.newWing(), "wingId", "newWing", false, errors);
		if (in.newArea() != null) {
			name(in.newArea().name(), "newArea.name", MAX_AREA_NAME, errors);
			if (in.newArea().pincode() == null || !PINCODE.matcher(in.newArea().pincode().trim()).matches()) {
				errors.add(field(LOCATION + "newArea.pincode", "pattern", "PIN code must be 6 digits."));
			}
		}
		if (in.newSociety() != null) {
			name(in.newSociety().name(), "newSociety.name", MAX_SOCIETY_NAME, errors);
			if (in.newSociety().kind() == null) {
				errors.add(field(LOCATION + "newSociety.kind", "required",
					"Use one of " + List.of(SocietyKind.values()) + "."));
			}
			optional(in.newSociety().developer(), "newSociety.developer", MAX_DEVELOPER, errors);
			optional(in.newSociety().street(), "newSociety.street", MAX_STREET, errors);
		}
		if (in.newTower() != null) {
			name(in.newTower().name(), "newTower.name", MAX_TOWER_NAME, errors);
		}
		if (in.newWing() != null) {
			name(in.newWing().name(), "newWing.name", MAX_TOWER_NAME, errors);
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		boolean hasSociety = in.societyId() != null || in.newSociety() != null;
		boolean hasTower = in.towerId() != null || in.newTower() != null;
		boolean hasWing = in.wingId() != null || in.newWing() != null;
		if (hasWing && !hasTower) {
			throw mismatch(in.wingId() != null ? "wingId" : "newWing", "A wing needs its tower.");
		}
		if (hasTower && !hasSociety) {
			throw mismatch(in.towerId() != null ? "towerId" : "newTower", "A tower needs its society.");
		}
		cityService.requireLive(cityCode);
		cityService.requirePincode(cityCode, pincode);
		City city = cities.getReferenceById(cityCode);
		
		Area area;
		if (in.areaId() != null) {
			area = usable(areas.findByPublicId(in.areaId()), Area::getStatus, Area::getCreatedBy, viewer, "areaId",
				"area");
			if (!area.getCity().getCode().equals(cityCode)) {
				throw mismatch("areaId", "This area is not in " + cityName(cityCode) + ".");
			}
		} else {
			String areaPincode = in.newArea().pincode().trim();
			requireAreaPincode(cityCode, areaPincode);
			area = pendingTwin(areas.findByCityCodeAndNameKey(cityCode, LocationNames.key(in.newArea().name())),
				Area::getStatus, "An area with this name already exists in " + cityName(cityCode) + "; pick it instead.");
			if (area == null) {
				area = new Area();
				area.setCity(city);
				area.setName(in.newArea().name());
				area.addPincode(areaPincode);
				proposed(area::setStatus, area::setCreatedBy, actor, viewer);
			}
		}
		if (!area.pincodeList().contains(pincode)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "pincode_not_in_area",
				"PIN code " + pincode + " is not in " + area.getName() + ".",
				List.of(field("pincode", "not_in_area", "Use one of " + area.pincodeList() + ".")));
		}
		
		Society society = null;
		if (in.societyId() != null) {
			society = usable(societies.findByPublicId(in.societyId()), Society::getStatus, Society::getCreatedBy,
				viewer, "societyId", "society");
			if (area.getId() == null || !society.getArea().getId().equals(area.getId())) {
				throw mismatch("societyId", "This society is not in " + area.getName() + ".");
			}
		} else if (in.newSociety() != null) {
			if (area.getId() != null) {
				society = pendingTwin(societies.findByAreaAndNameKey(area, LocationNames.key(in.newSociety().name())),
					Society::getStatus,
					"A society with this name already exists in " + area.getName() + "; pick it instead.");
			}
			if (society == null) {
				society = new Society();
				society.setArea(area);
				society.setName(in.newSociety().name());
				society.setKind(in.newSociety().kind());
				society.setDeveloper(LocationNames.clean(blankToNull(in.newSociety().developer())));
				society.setStreet(LocationNames.clean(blankToNull(in.newSociety().street())));
				proposed(society::setStatus, society::setCreatedBy, actor, viewer);
			}
		}
		
		SocietyTower tower = null;
		if (in.towerId() != null) {
			tower = usable(towers.findByPublicId(in.towerId()), SocietyTower::getStatus, SocietyTower::getCreatedBy,
				viewer, "towerId", "tower");
			if (society.getId() == null || !tower.getSociety().getId().equals(society.getId())) {
				throw mismatch("towerId", "This tower is not in " + society.getName() + ".");
			}
		} else if (in.newTower() != null) {
			if (society.getId() != null) {
				tower = pendingTwin(towers.findBySocietyAndNameKey(society, LocationNames.key(in.newTower().name())),
					SocietyTower::getStatus,
					"A tower with this name already exists in " + society.getName() + "; pick it instead.");
			}
			if (tower == null) {
				tower = new SocietyTower();
				tower.setSociety(society);
				tower.setName(in.newTower().name());
				tower.setKind(in.newTower().kind() == null ? TowerKind.TOWER : in.newTower().kind());
				tower.setSortOrder(society.getId() == null ? 0 : (int) towers.countBySociety(society));
				proposed(tower::setStatus, tower::setCreatedBy, actor, viewer);
			}
		}
		
		SocietyWing wing = null;
		if (in.wingId() != null) {
			wing = usable(wings.findByPublicId(in.wingId()), SocietyWing::getStatus, SocietyWing::getCreatedBy, viewer,
				"wingId", "wing");
			if (tower.getId() == null || !wing.getTower().getId().equals(tower.getId())) {
				throw mismatch("wingId", "This wing is not in " + tower.getName() + ".");
			}
		} else if (in.newWing() != null) {
			if (tower.getId() != null) {
				wing = pendingTwin(wings.findByTowerAndNameKey(tower, LocationNames.key(in.newWing().name())),
					SocietyWing::getStatus,
					"A wing with this name already exists in " + tower.getName() + "; pick it instead.");
			}
			if (wing == null) {
				wing = tower.addWing(in.newWing().name(), LocationStatus.PENDING, actor);
				proposed(wing::setStatus, wing::setCreatedBy, actor, viewer);
			}
		}
		
		try {
			if (area.getId() == null) {
				areas.saveAndFlush(area);
			}
			if (society != null && society.getId() == null) {
				societies.saveAndFlush(society);
			}
			if (tower != null && tower.getId() == null) {
				towers.saveAndFlush(tower);
			} else if (wing != null && wing.getId() == null) {
				wings.saveAndFlush(wing);
			}
		} catch (DataIntegrityViolationException e) {
			// a concurrent proposal with the same name won the unique (parent, name_key) key
			throw exists("A place with this name was just added; pick it instead.");
		}
		return new Placement(area, society, tower, wing);
	}
	
	// ---------------------------------------------------------------- helpers
	
	private Area readableArea(UUID publicId, Viewer viewer) {
		return areas.findByPublicId(publicId)
			.filter(a -> viewer.reads(a.getStatus(), a.getCreatedBy()))
			.orElseThrow(() -> ApiException.notFound("Area"));
	}
	
	private Area readableArea(UUID publicId, Viewer viewer, String field) {
		return areas.findByPublicId(publicId)
			.filter(a -> viewer.reads(a.getStatus(), a.getCreatedBy()))
			.orElseThrow(() -> unknown(field, "area"));
	}
	
	private Society readableSociety(UUID publicId, Viewer viewer) {
		return societies.findByPublicId(publicId)
			.filter(s -> viewer.reads(s.getStatus(), s.getCreatedBy()))
			.orElseThrow(() -> ApiException.notFound("Society"));
	}
	
	private Society readableSociety(UUID publicId, Viewer viewer, String field) {
		return societies.findByPublicId(publicId)
			.filter(s -> viewer.reads(s.getStatus(), s.getCreatedBy()))
			.orElseThrow(() -> unknown(field, "society"));
	}
	
	private List<TowerView> towerViews(Society society, Viewer viewer) {
		Map<Long, Long> perTower = new HashMap<>();
		Map<Long, Long> perWing = new HashMap<>();
		for (Object[] row : societies.countPublishedHomesByTowerAndWing(society)) {
			long count = ((Number) row[2]).longValue();
			perTower.merge((Long) row[0], count, Long::sum);
			if (row[1] != null) {
				perWing.merge((Long) row[1], count, Long::sum);
			}
		}
		return towers.findBySocietyOrderBySortOrderAscNameKeyAsc(society).stream()
			.filter(t -> viewer.lists(t.getStatus(), t.getCreatedBy()))
			.map(t -> new TowerView(t.getPublicId(), t.getName(), t.getKind(), t.getStatus(),
				perTower.getOrDefault(t.getId(), 0L), t.getWings().stream()
				.filter(w -> viewer.lists(w.getStatus(), w.getCreatedBy()))
				.map(w -> new TowerView.WingView(w.getPublicId(), w.getName(), w.getStatus(),
					perWing.getOrDefault(w.getId(), 0L)))
				.toList()))
			.toList();
	}
	
	private List<SocietyCard> societyCards(List<Society> rows) {
		List<Long> ids = rows.stream().map(Society::getId).toList();
		Map<Long, Long> homes = counts(ids.isEmpty() ? List.of() : societies.countPublishedHomes(ids));
		Map<Long, Long> towerCounts = counts(ids.isEmpty() ? List.of() : societies.countActiveTowers(ids));
		return rows.stream().map(s -> new SocietyCard(s.getPublicId(), s.getName(), s.getDeveloper(), s.getKind(),
			AreaRef.of(s.getArea()), CityRef.of(s.getCity()), s.getStreet(), s.getStatus(),
			towerCounts.getOrDefault(s.getId(), 0L), homes.getOrDefault(s.getId(), 0L))).toList();
	}
	
	/** The list rule of {@link Viewer#lists} as a criteria predicate. */
	private static Predicate listed(Root<?> root, CriteriaBuilder cb, Viewer viewer) {
		Predicate active = cb.equal(root.get("status"), LocationStatus.ACTIVE);
		Predicate pending = cb.equal(root.get("status"), LocationStatus.PENDING);
		if (viewer.admin()) {
			return cb.or(active, pending);
		}
		if (viewer.publicId() == null) {
			return active;
		}
		return cb.or(active, cb.and(pending,
			cb.equal(root.join("createdBy", JoinType.LEFT).get("publicId"), viewer.publicId())));
	}
	
	/** {@code query} starts the value or one of its space-separated words (D-82, D-108). */
	private static Predicate wordPrefix(CriteriaBuilder cb, Expression<String> value, String query) {
		String escaped = escapeLike(query);
		return cb.or(cb.like(value, escaped + "%", LIKE_ESCAPE), cb.like(value, "% " + escaped + "%", LIKE_ESCAPE),
			cb.like(value, "%," + escaped + "%", LIKE_ESCAPE));
	}
	
	private static Pageable stable(Pageable pageable) {
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by("id")));
	}
	
	private static Map<Long, Long> counts(List<Object[]> rows) {
		Map<Long, Long> counts = new HashMap<>();
		for (Object[] row : rows) {
			counts.put((Long) row[0], ((Number) row[1]).longValue());
		}
		return counts;
	}
	
	private static String query(String q) {
		String key = LocationNames.key(q);
		if (key == null || key.isEmpty()) {
			return null;
		}
		if (key.length() > MAX_QUERY) {
			throw ApiException.validation(List.of(field("q", "size", "At most " + MAX_QUERY + " characters.")));
		}
		return key;
	}
	
	private static String pincodeFilter(String pincode) {
		if (pincode == null || pincode.isBlank()) {
			return null;
		}
		String pin = pincode.trim();
		if (!PINCODE.matcher(pin).matches()) {
			throw ApiException.validation(List.of(field("pincode", "pattern", "PIN code must be 6 digits.")));
		}
		return pin;
	}
	
	private void requireAreaPincode(String cityCode, String pincode) {
		try {
			cityService.requirePincode(cityCode, pincode);
		} catch (ApiException e) {
			throw ApiException.validation(List.of(field(LOCATION + "newArea.pincode", "city_mismatch",
				"This PIN code is not in " + cityName(cityCode) + ".")));
		}
	}
	
	private String cityName(String cityCode) {
		return cities.findById(cityCode).map(City::getName).orElse(cityCode);
	}
	
	/** A row the actor may place a home in, else 422 on {@code field}. */
	private static <T> T usable(Optional<T> row, Function<T, LocationStatus> status,
		Function<T, User> createdBy, Viewer viewer, String field, String what) {
		T found = row.filter(r -> viewer.reads(status.apply(r), createdBy.apply(r)))
			.orElseThrow(() -> unknown(LOCATION + field, what));
		if (status.apply(found) == LocationStatus.HIDDEN) {
			throw ApiException.validation(List.of(field(LOCATION + field, "hidden",
				"This " + what + " no longer takes new homes; pick another or ask an admin.")));
		}
		return found;
	}
	
	/**
	 * The row a proposal joins: a {@code PENDING} row of the same name under the same parent is reused as is
	 * (its {@code created_by} unchanged); an {@code ACTIVE} or {@code HIDDEN} one is a {@code 409 location_exists}.
	 * Null when the name is new.
	 */
	private static <T> T pendingTwin(Optional<T> existing, Function<T, LocationStatus> status, String detail) {
		if (existing.isEmpty()) {
			return null;
		}
		if (status.apply(existing.get()) != LocationStatus.PENDING) {
			throw exists(detail);
		}
		return existing.get();
	}
	
	/** A landlord's proposal is {@code PENDING} until listing approval; an admin creates {@code ACTIVE} rows. */
	private static void proposed(Consumer<LocationStatus> status,
		Consumer<User> createdBy, User actor, Viewer viewer) {
		status.accept(viewer.admin() ? LocationStatus.ACTIVE : LocationStatus.PENDING);
		createdBy.accept(actor);
	}
	
	private static void oneOf(UUID id, Object proposal, String idField, String newField, boolean required,
		List<ApiProblem.FieldError> errors) {
		if (id != null && proposal != null) {
			errors.add(field(LOCATION + newField, "one_of", "Give " + idField + " or " + newField + ", not both."));
		} else if (required && id == null && proposal == null) {
			errors.add(field(LOCATION + idField, "required", "Pick an area or suggest one."));
		}
	}
	
	private static void name(String value, String fieldName, int max, List<ApiProblem.FieldError> errors) {
		String cleaned = LocationNames.clean(value);
		if (cleaned == null || cleaned.length() < 2 || cleaned.length() > max) {
			errors.add(field(LOCATION + fieldName, "size", "Must be 2 to " + max + " characters."));
		}
	}
	
	private static void optional(String value, String fieldName, int max, List<ApiProblem.FieldError> errors) {
		String cleaned = LocationNames.clean(value);
		if (cleaned != null && cleaned.length() > max) {
			errors.add(field(LOCATION + fieldName, "size", "At most " + max + " characters."));
		}
	}
	
	private static ApiProblem.FieldError field(String name, String code, String message) {
		return new ApiProblem.FieldError(name, code, message);
	}
	
	private static ApiException unknown(String fieldName, String what) {
		return ApiException.validation(List.of(field(fieldName, "not_found", "No such " + what + ".")));
	}
	
	private static ApiException mismatch(String fieldName, String detail) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "location_mismatch", detail,
			List.of(field(LOCATION + fieldName, "mismatch", detail)));
	}
	
	private static ApiException exists(String detail) {
		return ApiException.conflict("location_exists", detail);
	}
	
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
	
	static String escapeLike(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (char ch : value.toCharArray()) {
			if (ch == '%' || ch == '_' || ch == LIKE_ESCAPE) {
				escaped.append(LIKE_ESCAPE);
			}
			escaped.append(ch);
		}
		return escaped.toString();
	}
	
}
