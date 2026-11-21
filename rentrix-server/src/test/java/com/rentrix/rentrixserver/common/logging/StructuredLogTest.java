package com.rentrix.rentrixserver.common.logging;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

/** Logs are one JSON object per line (ECS by default) and carry the request id as {@code traceId}. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class StructuredLogTest {
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	AccessTokenService tokens;
	
	@Autowired
	ObjectMapper json;
	
	@Test
	void logLinesAreJsonWithTheTraceId(CapturedOutput output) throws Exception {
		mvc.perform(get("/test-only/log")
				.header("X-Request-Id", "log-trace-4242")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(UUID.randomUUID(), Set.of(Role.TENANT))))
			.andExpect(status().isOk());
		
		String line = output.getOut().lines()
			.filter(l -> l.contains("structured log probe"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("probe line not logged:\n" + output.getOut()));
		JsonNode node = json.readTree(line);
		assertThat(node.path("message").asString()).isEqualTo("structured log probe");
		assertThat(node.path("traceId").asString()).isEqualTo("log-trace-4242");
		assertThat(node.path("log").path("level").asString()).isEqualTo("INFO");
	}
	
}
