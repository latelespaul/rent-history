package com.rentrix.rentrixserver.city;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cities are data (D-20, D-82..D-84): the list, search, activation and pincode rule all follow
 * the {@code cities} rows. Tests that add rows run in a rolled-back transaction, so the shared
 * context keeps only the seed cities.
 */
@IntegrationTest
class CityApiTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	JdbcTemplate jdbc;
	
	@Autowired
	CityService cityService;
	
	private void insertCity(String code, String name, String aliases, String state, boolean live, String prefixes) {
		jdbc.update("insert into cities (code, name, aliases, state, live, pincode_prefixes, created_at, updated_at) "
			+ "values (?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)",
			code, name, aliases, state, live, prefixes);
	}
	
	@Test
	@Transactional
	void listsOnlyLiveRowsByName() throws Exception {
		insertCity("ZZA", "Aaa Dormant", null, "Nowhere", false, null);
		mvc.perform(get("/cities"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].code").value("BLR"))
			.andExpect(jsonPath("$[0].name").value("Bangalore"))
			.andExpect(jsonPath("$[0].aliases[0]").value("Bengaluru"))
			.andExpect(jsonPath("$[0].state").value("Karnataka"))
			.andExpect(jsonPath("$[1].code").value("PNQ"))
			.andExpect(jsonPath("$[1].aliases", hasSize(0)));
	}
	
	@Test
	void searchMatchesWordPrefixOfCodeNameAliasOrState() throws Exception {
		mvc.perform(get("/cities").param("q", "beng"))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].code").value("BLR"));
		mvc.perform(get("/cities").param("q", "MAHA"))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].code").value("PNQ"));
		mvc.perform(get("/cities").param("q", "pnq"))
			.andExpect(jsonPath("$[0].code").value("PNQ"));
		mvc.perform(get("/cities").param("q", "  "))
			.andExpect(jsonPath("$", hasSize(2)));
		mvc.perform(get("/cities").param("q", "galore"))
			.andExpect(jsonPath("$", hasSize(0)));
	}
	
	@Test
	@Transactional
	void searchMatchesMultiWordNamesFromAnyWord() throws Exception {
		insertCity("ZZN", "Navi Mumbai", null, "Maharashtra", true, null);
		for (String q : new String[] {"navi mumbai", "  Navi   MUMBAI ", "navi mu", "mumbai"}) {
			mvc.perform(get("/cities").param("q", q))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].code").value("ZZN"));
		}
		mvc.perform(get("/cities").param("q", "mumbai navi"))
			.andExpect(jsonPath("$", hasSize(0)));
	}
	
	@Test
	@Transactional
	void activatingARowNeedsNoCodeChange() throws Exception {
		insertCity("HYD", "Hyderabad", "Cyberabad, Secunderabad", "Telangana", false, "50");
		mvc.perform(get("/cities").param("q", "hyd")).andExpect(jsonPath("$", hasSize(0)));
		assertThatThrownBy(() -> cityService.requireLive("HYD")).isInstanceOf(ApiException.class);
		
		jdbc.update("update cities set live = true where code = 'HYD'");
		
		mvc.perform(get("/cities"))
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[1].code").value("HYD"));
		mvc.perform(get("/cities").param("q", "secu"))
			.andExpect(jsonPath("$[0].code").value("HYD"))
			.andExpect(jsonPath("$[0].aliases[1]").value("Secunderabad"));
		assertThatCode(() -> cityService.requireLive("HYD")).doesNotThrowAnyException();
		assertThatCode(() -> cityService.requirePincode("HYD", "500081")).doesNotThrowAnyException();
	}
	
	@Test
	void pincodeMustStartWithACityPrefix() {
		assertThatCode(() -> cityService.requirePincode("BLR", "560034")).doesNotThrowAnyException();
		assertThatCode(() -> cityService.requirePincode("PNQ", "411045")).doesNotThrowAnyException();
		assertPincodeError("BLR", "411045", "city_mismatch");
		assertPincodeError("PNQ", "560034", "city_mismatch");
		assertPincodeError("BLR", "56003", "pattern");
		assertPincodeError("BLR", "56003a", "pattern");
		assertPincodeError("BLR", null, "pattern");
	}
	
	@Test
	@Transactional
	void cityWithoutPrefixesAcceptsAnySixDigitPincode() {
		insertCity("ZZB", "Open City", null, "Anywhere", true, null);
		assertThatCode(() -> cityService.requirePincode("ZZB", "110001")).doesNotThrowAnyException();
		assertPincodeError("ZZB", "11000", "pattern");
	}
	
	@Test
	void notLiveErrorNamesNoCity() {
		for (String code : new String[] {"XXX", null}) {
			assertThatThrownBy(() -> cityService.requireLive(code))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getCode()).isEqualTo("city_not_live");
					assertThat(e.getMessage()).isEqualTo("Rentrix is not live in this city yet.");
				});
		}
		assertThatThrownBy(() -> cityService.requirePincode("XXX", "560034"))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("city_not_live"));
	}
	
	private void assertPincodeError(String city, String pincode, String fieldCode) {
		assertThatThrownBy(() -> cityService.requirePincode(city, pincode))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.getCode()).isEqualTo("validation_failed");
				assertThat(e.getErrors()).singleElement().satisfies(err -> {
					assertThat(err.field()).isEqualTo("pincode");
					assertThat(err.code()).isEqualTo(fieldCode);
				});
			});
	}
	
}
