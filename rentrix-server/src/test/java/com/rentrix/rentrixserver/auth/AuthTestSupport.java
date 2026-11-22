package com.rentrix.rentrixserver.auth;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helpers for auth tests: refresh cookie handling and a fresh client IP per sign-in (rate limit). */
public final class AuthTestSupport {
	
	private static final Pattern COOKIE = Pattern.compile(RefreshCookies.NAME + "=([^;]*)");
	
	private AuthTestSupport() {
	}
	
	/** A random client IP, so the shared test context never trips the sign-in rate limit by accident. */
	public static RequestPostProcessor freshIp() {
		String ip = "10." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256)
			+ "." + ThreadLocalRandom.current().nextInt(1, 255);
		return ip(ip);
	}
	
	public static RequestPostProcessor ip(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}
	
	public static String setCookieHeader(MockHttpServletResponse response) {
		return response.getHeader(HttpHeaders.SET_COOKIE);
	}
	
	public static String refreshToken(MockHttpServletResponse response) {
		String header = setCookieHeader(response);
		if (header == null) {
			return null;
		}
		Matcher m = COOKIE.matcher(header);
		return m.find() ? m.group(1) : null;
	}
	
	public static Cookie refreshCookie(String value) {
		return new Cookie(RefreshCookies.NAME, value);
	}
	
}
