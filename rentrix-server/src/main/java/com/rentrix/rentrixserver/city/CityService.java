package com.rentrix.rentrixserver.city;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The {@code cities} table is the only list of cities (D-20): nothing here names a city, so
 * adding or activating one is a row change, never a release (D-83).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {
	
	static final String NOT_LIVE_DETAIL = "Rentrix is not live in this city yet.";
	
	private static final Pattern PINCODE = Pattern.compile("\\d{6}");
	private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
	
	private final CityRepository cities;
	
	public List<CityResponse> liveCities() {
		return liveCities(null);
	}
	
	/**
	 * Live cities by name; a non-blank {@code q} keeps those where it is a case-insensitive prefix
	 * of any word of the code, name, an alias or the state, so "beng" finds Bangalore through its
	 * alias Bengaluru (D-82).
	 */
	public List<CityResponse> liveCities(String q) {
		String query = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
		return cities.findByLiveTrueOrderByNameAsc().stream()
			.filter(city -> query.isEmpty() || matches(city, query))
			.map(CityResponse::from)
			.toList();
	}
	
	/** Rejects any city that is not a live row (D-20). */
	public void requireLive(String code) {
		if (code == null || !cities.existsByCodeAndLiveTrue(code)) {
			throw notLive();
		}
	}
	
	/**
	 * Rejects a pincode that is not 6 digits or does not start with one of the city's
	 * {@code pincode_prefixes}; a city without prefixes accepts any 6-digit pincode (D-84).
	 * An unknown city is {@code city_not_live}; whether a known city must be live is the caller's
	 * check ({@link #requireLive}).
	 */
	public void requirePincode(String code, String pincode) {
		City city = code == null ? null : cities.findById(code).orElse(null);
		if (city == null) {
			throw notLive();
		}
		if (pincode == null || !PINCODE.matcher(pincode).matches()) {
			throw invalidPincode("pattern", "Pincode must be 6 digits.");
		}
		List<String> prefixes = city.pincodePrefixList();
		if (!prefixes.isEmpty() && prefixes.stream().noneMatch(pincode::startsWith)) {
			throw invalidPincode("city_mismatch", "This pincode is not in " + city.getName() + ".");
		}
	}
	
	private static boolean matches(City city, String query) {
		return Stream.concat(Stream.of(city.getCode(), city.getName(), city.getState()), city.aliasList().stream())
			.filter(Objects::nonNull)
			.flatMap(value -> WORD_SEPARATOR.splitAsStream(value.toLowerCase(Locale.ROOT)))
			.anyMatch(word -> word.startsWith(query));
	}
	
	private static ApiException notLive() {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "city_not_live", NOT_LIVE_DETAIL);
	}
	
	private static ApiException invalidPincode(String code, String message) {
		return ApiException.validation(List.of(new ApiProblem.FieldError("pincode", code, message)));
	}
	
}
