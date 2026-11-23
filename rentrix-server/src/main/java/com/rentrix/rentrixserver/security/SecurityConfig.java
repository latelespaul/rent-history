package com.rentrix.rentrixserver.security;

import com.rentrix.rentrixserver.common.error.ProblemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless security (D-06, D-62): no session, no CSRF token, no form or basic login. Every
 * request is authenticated only by its bearer token. Errors are problem+json (D-60).
 * Feature PRs add their public routes to {@link #PUBLIC_GET}. Callers who have not picked a role yet are
 * held to the onboarding allow-list by {@link OnboardingFilter} (D-73). The admin console lives on its own host
 * ({@link AdminHostFilter}, §13.3).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	/**
	 * Anonymous POST routes: sign-in, the cookie-based refresh and logout of the main site and of the admin console
	 * (§13, §13.3), and Truecaller's server callback.
	 */
	static final String[] PUBLIC_POST = {
		"/auth/google", "/auth/dev-login", "/auth/dev-login/new", "/auth/refresh", "/auth/logout",
		"/auth/admin/google", "/auth/admin/dev-login", "/auth/admin/refresh", "/auth/admin/logout",
		"/auth/truecaller/callback",
	};

	/** Anonymous GET routes (paths are relative to the /api/v1 context path). */
	static final String[] PUBLIC_GET = {
		"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness",
		"/cities", "/stats", "/properties", "/properties/*", "/properties/*/rating",
		"/areas", "/areas/*", "/societies", "/societies/*", "/societies/*/towers",
		"/auth/config", "/auth/dev-users", "/auth/admin/dev-users",
		// a private document through its 5-minute signed link: the signature is the permission (D-30, D-131)
		"/documents/*",
		// springdoc is only switched on in dev and test (D-64)
		"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
	};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenService tokens, AdminHosts adminHosts,
		ProblemWriter problems,
		AuthenticationEntryPoint entryPoint, AccessDeniedHandler accessDeniedHandler,
		CorsConfigurationSource corsConfigurationSource) throws Exception {
		http.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
				.requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.addFilterBefore(new JwtAuthenticationFilter(tokens, adminHosts), UsernamePasswordAuthenticationFilter.class)
			.addFilterAfter(new AdminHostFilter(adminHosts, problems), JwtAuthenticationFilter.class)
			.addFilterAfter(new OnboardingFilter(problems), AdminHostFilter.class);
		return http.build();
	}

	@Bean
	AuthenticationEntryPoint problemAuthenticationEntryPoint(ProblemWriter problems) {
		return (request, response, exception) -> {
			boolean badToken = request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE) != null;
			problems.write(request, response, HttpStatus.UNAUTHORIZED,
				badToken ? "token_invalid" : "unauthenticated",
				badToken ? "The access token is invalid or expired." : "Sign in to continue.");
		};
	}

	@Bean
	AccessDeniedHandler problemAccessDeniedHandler(ProblemWriter problems) {
		return (request, response, exception) -> problems.write(request, response, HttpStatus.FORBIDDEN, "forbidden",
			"You are not allowed to do this.");
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(cors.allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
		config.setExposedHeaders(List.of("X-Request-Id", "Retry-After", "Idempotent-Replayed"));
		config.setAllowCredentials(true); // refresh cookie (D-63)
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	/** No passwords and no user store (D-71); stops Spring Boot from creating a default user. */
	@Bean
	UserDetailsService noUserDetailsService() {
		return new InMemoryUserDetailsManager();
	}

}
