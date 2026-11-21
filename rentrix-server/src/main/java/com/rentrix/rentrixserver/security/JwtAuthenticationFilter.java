package com.rentrix.rentrixserver.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code Authorization: Bearer <jwt>} and, when the token verifies, authenticates the
 * request as {@link AuthenticatedUser} with {@code ROLE_*} authorities. A bad token leaves the
 * request anonymous and marks it, so protected endpoints answer {@code 401 token_invalid}
 * (the web then refreshes) while public endpoints still work.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	
	static final String INVALID_TOKEN_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".invalid";
	private static final String BEARER = "Bearer ";
	
	private final AccessTokenService tokens;
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			try {
				AuthenticatedUser user = tokens.verify(header.substring(BEARER.length()).trim());
				var authorities = user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
				var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (InvalidTokenException e) {
				request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
			}
		}
		chain.doFilter(request, response);
	}
	
}
