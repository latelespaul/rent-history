package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.common.error.ProblemWriter;
import com.rentrix.rentrixserver.user.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * Onboarding gate (D-73): a signed-in caller whose token holds neither {@code TENANT} nor {@code LANDLORD}
 * has not picked a role yet and may only call {@code GET /me}, {@code POST /me/onboarding},
 * {@code DELETE /me}, {@code /auth/**}, the public reads and the anonymous writes (the page view beacon and the
 * "Contact us" form); anything else is
 * {@code 403 onboarding_required}. Admin console sessions are not held here: {@link AdminHostFilter} limits them.
 * The check reads only the token's {@code roles} claim, which onboarding refreshes (D-62).
 */
@RequiredArgsConstructor
public class OnboardingFilter extends OncePerRequestFilter {
	
	private static final AntPathMatcher PATHS = new AntPathMatcher();
	
	private final ProblemWriter problems;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user && !user.adminSession()
			&& !user.hasRole(Role.TENANT) && !user.hasRole(Role.LANDLORD) && !allowed(request)) {
			problems.write(request, response, HttpStatus.FORBIDDEN, "onboarding_required",
				"Choose tenant, landlord or both to finish signing up.");
			return;
		}
		chain.doFilter(request, response);
	}
	
	static boolean allowed(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		String method = request.getMethod();
		if (HttpMethod.OPTIONS.matches(method) || path.equals("/error") || path.startsWith("/auth/")) {
			return true;
		}
		if (path.equals("/me")) {
			return HttpMethod.GET.matches(method) || HttpMethod.DELETE.matches(method);
		}
		if (path.equals("/me/onboarding")) {
			return HttpMethod.POST.matches(method);
		}
		if (HttpMethod.POST.matches(method)
			&& Arrays.stream(SecurityConfig.PUBLIC_POST).anyMatch(pattern -> PATHS.match(pattern, path))) {
			return true;
		}
		return HttpMethod.GET.matches(method)
			&& Arrays.stream(SecurityConfig.PUBLIC_GET).anyMatch(pattern -> PATHS.match(pattern, path));
	}
	
}
