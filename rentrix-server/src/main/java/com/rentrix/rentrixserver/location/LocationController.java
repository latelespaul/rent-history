package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.location.dto.AreaCard;
import com.rentrix.rentrixserver.location.dto.AreaDetail;
import com.rentrix.rentrixserver.location.dto.SocietyCard;
import com.rentrix.rentrixserver.location.dto.SocietyDetail;
import com.rentrix.rentrixserver.location.dto.TowerView;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Public reads of the location hierarchy (§12.2, D-108, D-112): areas by name or PIN code, societies, the area
 * and society pages and the tower / wing pickers. Signed-in landlords also see their own {@code PENDING}
 * proposals, admins every one (D-109). Ratings and the rating sorts come with B16.
 */
@RestController
@RequiredArgsConstructor
public class LocationController {
	
	/** Sort allow-list (D-59); B16 adds {@code top_rated}, {@code most_reviewed} and {@code recent}. */
	static final Map<String, String> SORTS = Map.of("name", "nameKey");
	static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "nameKey");
	
	private final LocationService service;
	
	/** {@code GET /areas?city=&q=&pincode=&page=&size=&sort=}: {@code q} is a word prefix of name or alias. */
	@GetMapping("/areas")
	public PageResponse<AreaCard> areas(@RequestParam(required = false) String city,
		@RequestParam(required = false) String q, @RequestParam(required = false) String pincode,
		@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
		@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser caller) {
		return service.areas(cityCode(city), q, pincode, caller,
			PageRequests.of(page, size, sort, SORTS, DEFAULT_SORT));
	}
	
	@GetMapping("/areas/{id}")
	public AreaDetail area(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return service.area(id, caller);
	}
	
	/** {@code GET /societies?city=&area=&q=&kind=&page=&size=&sort=}: {@code q} matches name or developer. */
	@GetMapping("/societies")
	public PageResponse<SocietyCard> societies(@RequestParam(required = false) String city,
		@RequestParam(required = false) UUID area, @RequestParam(required = false) String q,
		@RequestParam(required = false) String kind, @RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return service.societies(cityCode(city), area, q, kind(kind), caller,
			PageRequests.of(page, size, sort, SORTS, DEFAULT_SORT));
	}
	
	@GetMapping("/societies/{id}")
	public SocietyDetail society(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return service.society(id, caller);
	}
	
	@GetMapping("/societies/{id}/towers")
	public List<TowerView> towers(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return service.towers(id, caller);
	}
	
	private static String cityCode(String city) {
		return city == null || city.isBlank() ? null : city.trim().toUpperCase(Locale.ROOT);
	}
	
	private static SocietyKind kind(String kind) {
		if (kind == null || kind.isBlank()) {
			return null;
		}
		try {
			return SocietyKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("kind", "not_allowed",
				"Use one of " + List.of(SocietyKind.values()) + ".")));
		}
	}
	
}
