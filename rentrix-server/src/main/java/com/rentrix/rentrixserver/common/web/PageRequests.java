package com.rentrix.rentrixserver.common.web;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a {@link Pageable} from {@code ?page=&size=&sort=field,dir} (D-59): 0-based page,
 * size 1–50 (default 20), sort only from the endpoint's allow-list. Bad values are
 * rejected with {@code 422 validation_failed} instead of being silently clamped.
 */
public final class PageRequests {
	
	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 50;
	
	private PageRequests() {
	}
	
	/**
	 * @param allowedSorts API sort name → entity property path, e.g. {@code "rating" → "bayesScore"}
	 * @param defaultSort  used when {@code sort} is absent
	 */
	public static Pageable of(Integer page, Integer size, String sort, Map<String, String> allowedSorts,
		Sort defaultSort) {
		List<ApiProblem.FieldError> errors = new ArrayList<>();
		int number = page == null ? 0 : page;
		int pageSize = size == null ? DEFAULT_SIZE : size;
		if (number < 0) {
			errors.add(new ApiProblem.FieldError("page", "min", "Must be 0 or more."));
		}
		if (pageSize < 1 || pageSize > MAX_SIZE) {
			errors.add(new ApiProblem.FieldError("size", "range", "Must be between 1 and " + MAX_SIZE + "."));
		}
		Sort resolved = defaultSort;
		if (sort != null && !sort.isBlank()) {
			String[] parts = sort.split(",", -1);
			String property = allowedSorts.get(parts[0].trim());
			String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
			if (property == null || parts.length > 2 || !(direction.equals("asc") || direction.equals("desc"))) {
				errors.add(new ApiProblem.FieldError("sort", "not_allowed",
					"Use one of " + allowedSorts.keySet() + " with ,asc or ,desc."));
			} else {
				resolved = Sort.by(direction.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, property);
			}
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		return PageRequest.of(number, pageSize, resolved);
	}
	
}
