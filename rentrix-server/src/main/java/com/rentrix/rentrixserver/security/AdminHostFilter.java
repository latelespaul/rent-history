package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.common.error.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Keeps the admin console on its own host (D-19, §13.3):
 * <ul>
 * <li>{@code /admin/**} and the admin sign-in ({@code /auth/admin/**}) answer {@code 404} off an admin host;</li>
 * <li>the main site's sign-in, refresh and logout answer {@code 404} on an admin host, so a session there is always an
 * admin session;</li>
 * <li>an admin session reads anything but writes only through {@code /admin/**} and {@code /auth/admin/**}
 * ({@code 403 admin_console_only}): an admin is never a tenant or landlord in the console.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class AdminHostFilter extends OncePerRequestFilter {
	
	/** The main site's own session endpoints; the admin host has {@code /auth/admin/…} instead. */
	static final Set<String> MAIN_SESSION_PATHS = Set.of("/auth/google", "/auth/refresh", "/auth/logout",
		"/auth/dev-users", "/auth/dev-login", "/auth/dev-login/new");
	
	private final AdminHosts adminHosts;
	private final ProblemWriter problems;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		boolean consolePath = isConsolePath(path);
		boolean adminHost = adminHosts.isAdminRequest(request);
		if (adminHost ? MAIN_SESSION_PATHS.contains(path) : consolePath) {
			problems.write(request, response, HttpStatus.NOT_FOUND, "not_found", "Not found.");
			return;
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (adminHost && !consolePath && !isRead(request.getMethod())
			&& auth != null && auth.getPrincipal() instanceof AuthenticatedUser user && user.adminSession()) {
			problems.write(request, response, HttpStatus.FORBIDDEN, "admin_console_only",
				"The admin console changes records only through its own actions.");
			return;
		}
		chain.doFilter(request, response);
	}
	
	static boolean isConsolePath(String path) {
		return path.equals("/admin") || path.startsWith("/admin/") || path.startsWith("/auth/admin/");
	}
	
	private static boolean isRead(String method) {
		return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method);
	}
	
}
