package com.rentrix.rentrixserver.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /auth/truecaller/callback}: Truecaller's server posts here (the URL registered for the web app), so
 * it is anonymous and carries only {@code requestId} (our nonce) and either {@code status}
 * ({@code flow_invoked}, {@code user_rejected}) or {@code accessToken} and {@code endpoint}. Truecaller expects a
 * 2xx within 3 s: the answer is {@code 200} at once for any JSON object (unknown or used nonces are ignored) and
 * the profile is fetched in the background ({@link TruecallerService}). The body is never logged.
 */
@RestController
@RequestMapping("/auth/truecaller")
@RequiredArgsConstructor
public class TruecallerCallbackController {
	
	private final TruecallerService truecaller;
	private final ObjectMapper json;
	
	@PostMapping(path = "/callback", consumes = "*/*")
	public ResponseEntity<Void> callback(@RequestBody(required = false) String body) {
		JsonNode node;
		try {
			node = json.readTree(body == null ? "" : body);
		} catch (JacksonException e) {
			return ResponseEntity.badRequest().build();
		}
		if (node == null || !node.isObject()) {
			return ResponseEntity.badRequest().build();
		}
		truecaller.callback(text(node, "requestId"), text(node, "status"), text(node, "accessToken"),
			text(node, "endpoint"));
		return ResponseEntity.ok().build();
	}
	
	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && value.isString() ? value.asString() : null;
	}
	
}
