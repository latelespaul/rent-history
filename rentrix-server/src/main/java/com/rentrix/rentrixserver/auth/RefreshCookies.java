package com.rentrix.rentrixserver.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * The refresh cookies (D-63): {@code HttpOnly; Secure; SameSite=Strict}, so scripts can never read them. The main
 * site's is {@code rentrix_refresh} on {@code Path=/api/v1/auth}; the admin console's (§13.3) is
 * {@code rentrix_admin_refresh} on {@code Path=/api/v1/auth/admin}. Neither sets {@code Domain}, so each stays on the
 * host that set it.
 */
public final class RefreshCookies {
	
	public static final String NAME = "rentrix_refresh";
	public static final String ADMIN_NAME = "rentrix_admin_refresh";
	
	private RefreshCookies() {
	}
	
	public static String issue(HttpServletRequest request, String token, Duration ttl) {
		return issue(request, token, ttl, false);
	}
	
	public static String issue(HttpServletRequest request, String token, Duration ttl, boolean admin) {
		return base(request, token, admin).maxAge(ttl).build().toString();
	}
	
	public static String clear(HttpServletRequest request) {
		return clear(request, false);
	}
	
	public static String clear(HttpServletRequest request, boolean admin) {
		return base(request, "", admin).maxAge(0).build().toString();
	}
	
	public static String read(HttpServletRequest request) {
		return read(request, false);
	}
	
	public static String read(HttpServletRequest request, boolean admin) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		String name = admin ? ADMIN_NAME : NAME;
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
	
	private static ResponseCookie.ResponseCookieBuilder base(HttpServletRequest request, String value, boolean admin) {
		return ResponseCookie.from(admin ? ADMIN_NAME : NAME, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path(request.getContextPath() + (admin ? "/auth/admin" : "/auth"));
	}
	
}
