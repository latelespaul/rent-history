package com.rentrix.rentrixserver.support;

import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.user.User;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Requests to the admin console (§13.3): its host ({@code ADMIN_HOSTS} in the test profile) and an admin token. */
public final class AdminConsole {
	
	public static final String HOST = "admin.localhost";
	
	private AdminConsole() {
	}
	
	/** Sends the request to the admin host. */
	public static RequestPostProcessor adminHost() {
		return request -> {
			request.setServerName(HOST);
			return request;
		};
	}
	
	/** {@code Authorization} value of an admin console session for {@code user}. */
	public static String adminBearer(AccessTokenService tokens, User user) {
		return "Bearer " + tokens.issueAdmin(user.getPublicId());
	}
	
}
