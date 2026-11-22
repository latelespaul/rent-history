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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
			.andExpect(jsonPath("$", hasSize(7)))
			.andExpect(jsonPath("$[*].code").value(contains("BLR", "DEL", "GGN", "HYD", "MUM", "NOI", "PNQ")))
			.andExpect(jsonPath("$[0].name").value("Bangalore"))
			.andExpect(jsonPath("$[0].aliases[0]").value("Bengaluru"))
			.andExpect(jsonPath("$[0].state").value("Karnataka"))
			.andExpect(jsonPath("$[0].region").value(nullValue()))
			.andExpect(jsonPath("$[1].region").value("Delhi NCR"))
			.andExpect(jsonPath("$[6].code").value("PNQ"))
			.andExpect(jsonPath("$[6].aliases", hasSize(0)));
	}
	
	@Test
	void searchMatchesWordPrefixOfCodeNameAliasOrState() throws Exception {
		mvc.perform(get("/cities").param("q", "beng"))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].code").value("BLR"));
		mvc.perform(get("/cities").param("q", "MAHA"))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].code").value("MUM"))
			.andExpect(jsonPath("$[1].code").value("PNQ"));
		mvc.perform(get("/cities").param("q", "ncr"))
			.andExpect(jsonPath("$[*].code").value(contains("DEL", "GGN", "NOI")));
		mvc.perform(get("/cities").param("q", "gurgaon"))
			.andExpect(jsonPath("$[0].code").value("GGN"));
		mvc.perform(get("/cities").param("q", "pnq"))
			.andExpect(jsonPath("$[0].code").value("PNQ"));
		mvc.perform(get("/cities").param("q", "  "))
			.andExpect(jsonPath("$", hasSize(7)));
		mvc.perform(get("/cities").param("q", "galore"))
			.andExpect(jsonPath("$", hasSize(0)));
	}
	
	@Test
	@Transactional
	void searchMatchesMultiWordNamesFromAnyWord() throws Exception {
		insertCity("ZZN", "Navi Mumbai", null, "Maharashtra", true, null);
		for (String q : new String[] {"navi mumbai", "  Navi   MUMBAI ", "navi mu"}) {
			mvc.perform(get("/cities").param("q", q))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].code").value("ZZN"));
		}
		mvc.perform(get("/cities").param("q", "mumbai"))
			.andExpect(jsonPath("$[*].code").value(contains("MUM", "ZZN")));
		mvc.perform(get("/cities").param("q", "mumbai navi"))
			.andExpect(jsonPath("$", hasSize(0)));
	}
	
	@Test
	@Transactional
	void activatingARowNeedsNoCodeChange() throws Exception {
		insertCity("MAA", "Chennai", "Madras, Tambaram", "Tamil Nadu", false, "60");
		mvc.perform(get("/cities").param("q", "chen")).andExpect(jsonPath("$", hasSize(0)));
		assertThatThrownBy(() -> cityService.requireLive("MAA")).isInstanceOf(ApiException.class);
		
		jdbc.update("update cities set live = true where code = 'MAA'");
		
		mvc.perform(get("/cities"))
			.andExpect(jsonPath("$", hasSize(8)))
			.andExpect(jsonPath("$[1].code").value("MAA"));
		mvc.perform(get("/cities").param("q", "tamb"))
			.andExpect(jsonPath("$[0].code").value("MAA"))
			.andExpect(jsonPath("$[0].aliases[1]").value("Tambaram"));
		assertThatCode(() -> cityService.requireLive("MAA")).doesNotThrowAnyException();
		assertThatCode(() -> cityService.requirePincode("MAA", "600041")).doesNotThrowAnyException();
	}
	
	@Test
	void pincodeMustStartWithACityPrefix() {
		assertThatCode(() -> cityService.requirePincode("BLR", "560034")).doesNotThrowAnyException();
		assertThatCode(() -> cityService.requirePincode("PNQ", "411045")).doesNotThrowAnyException();
		assertPincodeError("BLR", "411045", "city_mismatch");
		assertPincodeError("PNQ", "560034", "city_mismatch");
		assertThatCode(() -> cityService.requirePincode("GGN", "122002")).doesNotThrowAnyException();
		assertThatCode(() -> cityService.requirePincode("NOI", "201301")).doesNotThrowAnyException();
		assertThatCode(() -> cityService.requirePincode("DEL", "110017")).doesNotThrowAnyException();
		assertPincodeError("GGN", "110017", "city_mismatch");
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
					assertThat(e.getMessage()).isEqualTo("We are not live in this city yet.");
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
