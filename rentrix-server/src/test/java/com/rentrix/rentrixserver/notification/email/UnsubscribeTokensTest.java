package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.security.JwtProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UnsubscribeTokensTest {
	
	private final UnsubscribeTokens tokens =
		new UnsubscribeTokens(new JwtProperties("test-only-jwt-secret-not-for-production-0123456789", null));
	
	@Test
	void aTokenNamesItsUser() {
		UUID user = UUID.randomUUID();
		assertThat(tokens.verify(tokens.issue(user))).contains(user);
	}
	
	@Test
	void aTokenCannotBeForgedOrMovedToAnotherUser() {
		UUID asha = UUID.randomUUID();
		UUID ravi = UUID.randomUUID();
		String token = tokens.issue(asha);
		String signature = token.substring(token.indexOf('.') + 1);
		assertThat(tokens.verify(ravi + "." + signature)).isEmpty();
		assertThat(tokens.verify(asha + "." + signature.substring(1) + "A")).isEmpty();
		assertThat(tokens.verify(asha.toString().toUpperCase() + "." + signature)).isEmpty();
		assertThat(tokens.verify(asha.toString())).isEmpty();
		assertThat(tokens.verify("not-a-token")).isEmpty();
		assertThat(tokens.verify("")).isEmpty();
		assertThat(tokens.verify(null)).isEmpty();
		UnsubscribeTokens otherSecret =
			new UnsubscribeTokens(new JwtProperties("another-secret-of-at-least-thirty-two-bytes-long", null));
		assertThat(otherSecret.verify(token)).isEmpty();
	}
	
}
