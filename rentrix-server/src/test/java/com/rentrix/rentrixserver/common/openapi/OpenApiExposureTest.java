package com.rentrix.rentrixserver.common.openapi;

import com.rentrix.rentrixserver.RentrixServerApplication;
import com.rentrix.rentrixserver.support.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/** springdoc serves the OpenAPI contract (D-61) only when dev or test is active (D-64). */
class OpenApiExposureTest {

	@Nested
	@IntegrationTest
	class TestProfile {

		@Autowired
		MockMvc mvc;

		@Test
		void apiDocsArePublishedWithoutSignIn() throws Exception {
			mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists())
				.andExpect(jsonPath("$.paths['/cities']").exists());
		}

	}

	@Test
	void prodProfileHidesApiDocsAndSwaggerUi() throws Exception {
		try (ConfigurableApplicationContext app = new SpringApplicationBuilder(RentrixServerApplication.class)
			.profiles("prod")
			// command-line args: they win over the ${ENV:} placeholders in application.yaml
			.run("--server.port=0",
				"--server.address=127.0.0.1",
				"--spring.datasource.url=jdbc:sqlite:target/test-data/prod-" + UUID.randomUUID() + ".db",
				"--app.jwt.secret=a-production-secret-that-is-long-enough-0123456789",
				"--app.documents.encryption-key=a-production-document-key-long-enough-0123456789",
				"--app.cors.allowed-origins=https://rentrix.example",
				"--app.scheduling.enabled=false",
				"--logging.level.root=WARN")) {
			int port = ((WebServerApplicationContext) app).getWebServer().getPort();
			HttpClient http = HttpClient.newHttpClient();
			assertThat(statusOf(http, port, "/api/v1/actuator/health")).isEqualTo(200);
			assertThat(statusOf(http, port, "/api/v1/v3/api-docs")).isEqualTo(404);
			assertThat(statusOf(http, port, "/api/v1/swagger-ui/index.html")).isEqualTo(404);
		}
	}

	private static int statusOf(HttpClient http, int port, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

}
