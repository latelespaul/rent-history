package com.rentrix.rentrixserver.search;

import com.rentrix.rentrixserver.city.CityService;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** {@code GET /search/suggestions?city=&q=} (D-164): public, like {@code GET /properties} (D-162). */
@RestController
@RequiredArgsConstructor
public class SearchSuggestionController {
	
	/** Same bound as {@code GET /properties?q=}. */
	static final int MAX_TEXT = 100;
	
	private final SearchSuggestionService service;
	
	/**
	 * {@code q} is what the box holds so far: a society or area name (typos allowed), optionally a tower, and
	 * optionally a flat number at the end. {@code city} is one live city code or several, comma-separated, as for {@code GET /properties}; without it every live city. With
	 * {@code society} (a picked society's id) the text is a flat number or a tower of that society (D-167).
	 */
	@GetMapping("/search/suggestions")
	public SearchSuggestions suggestions(@RequestParam(required = false) String city,
		@RequestParam(required = false) String q, @RequestParam(required = false) UUID society,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		if (q != null && q.trim().length() > MAX_TEXT) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("q", "size",
				"At most " + MAX_TEXT + " characters.")));
		}
		if (society != null) {
			// the second step of the box: a society is picked, the text is a flat number or a tower (D-167)
			return service.suggestInSociety(society, q, caller);
		}
		return service.suggest(CityService.searchCodes(city), q, caller);
	}
	
}
