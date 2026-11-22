package com.rentrix.rentrixserver.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * The refresh cookie (D-63): {@code HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth}, so the browser
 * sends it only to the auth endpoints and scripts can never read it.
 */
public final class RefreshCookies {
	
	public static final String NAME = "rentrix_refresh";
	
	private RefreshCookies() {
	}
	
	public static String issue(HttpServletRequest request, String token, Duration ttl) {
		return base(request, token).maxAge(ttl).build().toString();
	}
	
	public static String clear(HttpServletRequest request) {
		return base(request, "").maxAge(0).build().toString();
	}
	
	public static String read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
	
	private static ResponseCookie.ResponseCookieBuilder base(HttpServletRequest request, String value) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path(request.getContextPath() + "/auth");
	}
	
}
