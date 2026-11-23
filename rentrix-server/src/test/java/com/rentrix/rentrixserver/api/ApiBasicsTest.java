package com.rentrix.rentrixserver.api;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

/** Health, cities, stateless JWT authentication and the problem+json error format (D-60). */
@IntegrationTest
class ApiBasicsTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	AccessTokenService tokens;
	
	private String bearer(Role... roles) {
		return "Bearer " + tokens.issue(UUID.randomUUID(), Set.of(roles));
	}
	
	@Test
	void healthAndProbesArePublicWithoutDetails() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());
		mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		mvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}
	
	@Test
	void citiesListsTheLiveSeedRows() throws Exception {
		mvc.perform(get("/cities"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(7)))
			.andExpect(jsonPath("$[0].code").value("BLR"))
			.andExpect(jsonPath("$[0].aliases[0]").value("Bengaluru"))
			.andExpect(jsonPath("$[6].code").value("PNQ"));
	}
	
	@Test
	void protectedRouteWithoutTokenIs401Problem() throws Exception {
		mvc.perform(get("/test-only/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("unauthenticated"))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.type").value("https://doorwayliving.in/problems/unauthenticated"))
			.andExpect(jsonPath("$.traceId").value(not(emptyOrNullString())));
	}
	
	@Test
	void badTokenIs401TokenInvalid() throws Exception {
		mvc.perform(get("/test-only/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("token_invalid"));
	}
	
	@Test
	void validTokenAuthenticatesWithoutSession() throws Exception {
		UUID id = UUID.randomUUID();
		mvc.perform(get("/test-only/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(id, Set.of(Role.TENANT))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.publicId").value(id.toString()))
			.andExpect(jsonPath("$.roles[0]").value("TENANT"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
	
	@Test
	void missingRoleIs403Problem() throws Exception {
		mvc.perform(get("/test-only/landlord-only").header(HttpHeaders.AUTHORIZATION, bearer(Role.TENANT)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("forbidden"));
		mvc.perform(get("/test-only/landlord-only").header(HttpHeaders.AUTHORIZATION, bearer(Role.LANDLORD)))
			.andExpect(status().isOk());
		// The admin console is on its own host (§13.3): not found on the main site, and an admin role
		// claimed by a main-site token counts for nothing there.
		mvc.perform(get("/admin/anything").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/admin/anything").with(adminHost()).header(HttpHeaders.AUTHORIZATION, bearer(Role.LANDLORD)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("token_invalid"));
	}
	
	@Test
	void unknownRouteIs404Problem() throws Exception {
		mvc.perform(get("/no-such-route").header(HttpHeaders.AUTHORIZATION, bearer(Role.TENANT)))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("not_found"))
			.andExpect(jsonPath("$.instance").value("/no-such-route"));
	}
	
	@Test
	void invalidBodyIs422WithFieldErrors() throws Exception {
		mvc.perform(post("/test-only/echo").header(HttpHeaders.AUTHORIZATION, bearer(Role.TENANT))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\",\"rating\":9}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors", hasSize(2)))
			.andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("not_blank"))
			.andExpect(jsonPath("$.errors[?(@.field == 'rating')].code").value("max"));
	}
	
	@Test
	void malformedJsonIs400() throws Exception {
		mvc.perform(post("/test-only/echo").header(HttpHeaders.AUTHORIZATION, bearer(Role.TENANT))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{oops"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("malformed_request"));
	}
	
	@Test
	void requestIdIsEchoedAndUsedAsTraceId() throws Exception {
		mvc.perform(get("/test-only/me").header("X-Request-Id", "client-req-12345"))
			.andExpect(header().string("X-Request-Id", "client-req-12345"))
			.andExpect(jsonPath("$.traceId").value("client-req-12345"));
	}
	
	@Test
	void paginationRejectsOversizedPagesAndUnknownSorts() throws Exception {
		mvc.perform(get("/test-only/page").header(HttpHeaders.AUTHORIZATION, bearer(Role.TENANT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(20));
		mvc.perform(get("/test-only/page?size=51&sort=password,desc").header(HttpHeaders.AUTHORIZATION, bearer(Role.TENANT)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[?(@.field == 'size')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'sort')]").exists());
	}
	
	@Test
	void corsAllowsOnlyConfiguredOrigins() throws Exception {
		mvc.perform(get("/cities").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
		mvc.perform(get("/cities").header(HttpHeaders.ORIGIN, "https://evil.example"))
			.andExpect(status().isForbidden());
	}
	
}
