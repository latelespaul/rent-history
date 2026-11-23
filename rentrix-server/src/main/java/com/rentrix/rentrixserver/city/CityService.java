package com.rentrix.rentrixserver.city;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The {@code cities} table is the only list of cities (D-20): nothing here names a city, so
 * adding or activating one is a row change, never a release (D-83).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {
	
	static final String NOT_LIVE_DETAIL = "We are not live in this city yet.";
	
	private static final Pattern PINCODE = Pattern.compile("\\d{6}");
	private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
	
	private final CityRepository cities;
	
	/**
	 * Live cities by name; a non-blank {@code q} keeps those where it is a case-insensitive prefix
	 * of the code, name, an alias or the state starting at any word, so "beng" finds Bangalore
	 * through its alias Bengaluru and "navi mumbai" or "mumbai" find Navi Mumbai (D-82).
	 */
	public List<CityResponse> liveCities(String q) {
		String query = String.join(" ", words(q));
		return cities.findByLiveTrueOrderByNameAsc().stream()
			.filter(city -> query.isEmpty() || matches(city, query))
			.map(CityResponse::from)
			.toList();
	}
	
	/**
	 * The city a user typed in "Add your home" (D-143): an existing row with that name or alias (live or not), else
	 * a new pending row ({@code live = false}) for an admin to approve. Its code is the first letters of the name,
	 * made unique with a number.
	 */
	@Transactional
	public City findOrCreatePending(String name, String state) {
		String clean = name.trim().replaceAll("\\s+", " ");
		List<String> wanted = words(clean);
		for (City city : cities.findAll()) {
			boolean same = Stream.concat(Stream.of(city.getName()), city.aliasList().stream())
				.map(CityService::words)
				.anyMatch(wanted::equals);
			if (same) {
				return city;
			}
		}
		String letters = clean.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
		String base = (letters + "XXX").substring(0, 3);
		String code = base;
		for (int n = 2; cities.existsById(code); n++) {
			code = base + n;
		}
		return cities.saveAndFlush(City.pending(code, clean, state.trim()));
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
		String problem = pincodeProblem(city, pincode);
		if (problem != null) {
			throw invalidPincode(problem.startsWith("Pincode must") ? "pattern" : "city_mismatch", problem);
		}
	}
	
	/**
	 * {@link #requirePincode}'s answer without throwing, for a check that only reports it ("Add your home" step 1):
	 * null when the city is live and the pincode fits it.
	 */
	public String pincodeProblem(String code, String pincode) {
		City city = code == null ? null : cities.findById(code).filter(City::isLive).orElse(null);
		return city == null ? "We are not live in this city yet." : pincodeProblem(city, pincode);
	}
	
	private static String pincodeProblem(City city, String pincode) {
		if (pincode == null || !PINCODE.matcher(pincode).matches()) {
			return "Pincode must be 6 digits.";
		}
		List<String> prefixes = city.pincodePrefixList();
		if (!prefixes.isEmpty() && prefixes.stream().noneMatch(pincode::startsWith)) {
			return "This pincode is not in " + city.getName() + ".";
		}
		return null;
	}
	
	private static boolean matches(City city, String query) {
		return Stream.concat(Stream.of(city.getCode(), city.getName(), city.getState(), city.getRegion()),
				city.aliasList().stream())
			.map(CityService::words)
			.anyMatch(words -> IntStream.range(0, words.size())
				.anyMatch(i -> String.join(" ", words.subList(i, words.size())).startsWith(query)));
	}
	
	private static List<String> words(String value) {
		return value == null ? List.of() : WORD_SEPARATOR.splitAsStream(value.toLowerCase(Locale.ROOT))
			.filter(word -> !word.isEmpty())
			.toList();
	}
	
	private static ApiException notLive() {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "city_not_live", NOT_LIVE_DETAIL);
	}
	
	private static ApiException invalidPincode(String code, String message) {
		return ApiException.validation(List.of(new ApiProblem.FieldError("pincode", code, message)));
	}
	
}
