package com.rentrix.rentrixserver.common.error;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

/**
 * Every error status the API uses comes back as the same RFC 9457 shape (D-60):
 * problem+json, {@code type} built from the stable {@code code}, a title, the status,
 * the request path as {@code instance} and the request id as {@code traceId}.
 */
@IntegrationTest
class ErrorShapeTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	AccessTokenService tokens;
	
	private MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(UUID.randomUUID(), Set.of(Role.TENANT)));
	}
	
	private ResultActions expectProblem(MockHttpServletRequestBuilder request, int status, String code, String path)
		throws Exception {
		return mvc.perform(request.header("X-Request-Id", "shape-test-0001"))
			.andExpect(status().is(status))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("https://rentrix.in/problems/" + code.replace('_', '-')))
			.andExpect(jsonPath("$.title").value(not(emptyOrNullString())))
			.andExpect(jsonPath("$.status").value(status))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.instance").value(path))
			.andExpect(jsonPath("$.traceId").value("shape-test-0001"));
	}
	
	@Test
	void status400Malformed() throws Exception {
		expectProblem(signedIn(post("/test-only/echo")).contentType(MediaType.APPLICATION_JSON).content("{oops"), 400,
			"malformed_request", "/test-only/echo");
		expectProblem(signedIn(get("/test-only/page?page=abc")), 400, "malformed_request", "/test-only/page");
	}
	
	@Test
	void status401WithoutOrWithBadToken() throws Exception {
		expectProblem(get("/test-only/me"), 401, "unauthenticated", "/test-only/me");
		expectProblem(get("/test-only/me").header(HttpHeaders.AUTHORIZATION, "Bearer nope"), 401, "token_invalid",
			"/test-only/me");
	}
	
	@Test
	void status403MissingRole() throws Exception {
		expectProblem(signedIn(get("/test-only/landlord-only")), 403, "forbidden", "/test-only/landlord-only");
	}
	
	@Test
	void status404UnknownRoute() throws Exception {
		expectProblem(signedIn(get("/nothing-here")), 404, "not_found", "/nothing-here");
	}
	
	@Test
	void status405WrongMethod() throws Exception {
		expectProblem(signedIn(delete("/test-only/echo")), 405, "method_not_allowed", "/test-only/echo");
	}
	
	@Test
	void status409Conflict() throws Exception {
		expectProblem(signedIn(get("/test-only/conflict")), 409, "test_conflict", "/test-only/conflict")
			.andExpect(jsonPath("$.title").value("Test conflict"))
			.andExpect(jsonPath("$.detail").value("Already exists."));
	}
	
	@Test
	void status415UnsupportedMediaType() throws Exception {
		expectProblem(signedIn(post("/test-only/echo")).contentType(MediaType.TEXT_PLAIN).content("hi"), 415,
			"unsupported_media_type", "/test-only/echo");
	}
	
	@Test
	void status422ValidationHasFieldErrors() throws Exception {
		expectProblem(signedIn(post("/test-only/echo")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\",\"rating\":0}"), 422, "validation_failed", "/test-only/echo")
			.andExpect(jsonPath("$.title").value("Validation failed"))
			.andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("not_blank"))
			.andExpect(jsonPath("$.errors[?(@.field == 'rating')].code").value("min"))
			.andExpect(jsonPath("$.errors[0].message").value(not(emptyOrNullString())));
	}
	
	@Test
	void status429HasRetryAfter() throws Exception {
		expectProblem(signedIn(get("/test-only/rate-limited")), 429, "rate_limited", "/test-only/rate-limited")
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"));
	}
	
	@Test
	void status500HidesTheCause() throws Exception {
		expectProblem(signedIn(get("/test-only/boom")), 500, "internal_error", "/test-only/boom")
			.andExpect(jsonPath("$.detail").value("Something went wrong."))
			.andExpect(content().string(not(org.hamcrest.Matchers.containsString("internal secret detail"))));
	}
	
	@Test
	void successResponsesAreNotProblems() throws Exception {
		mvc.perform(signedIn(get("/test-only/me")))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}
	
	@Test
	void titleComesFromTheCode() {
		org.assertj.core.api.Assertions.assertThat(ApiProblem.title("review_window_closed"))
			.isEqualTo("Review window closed");
		org.assertj.core.api.Assertions.assertThat(ApiProblem.title("")).isEqualTo("Error");
	}
	
}
