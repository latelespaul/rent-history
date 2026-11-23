package com.rentrix.rentrixserver.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Tells admin console requests from main-site requests by the request's host (D-19, §13.3): the {@code Host} header,
 * or {@code X-Forwarded-Host} when the peer is a trusted proxy ({@code TRUSTED_PROXIES}, Tomcat {@code RemoteIpValve}),
 * so a client cannot pick it through a proxy. Admin sessions and {@code /admin/**} work only on these hosts; the main
 * site never sees the admin role.
 */
@Component
public class AdminHosts {
	
	private final Set<String> hosts;
	
	public AdminHosts(AdminConsoleProperties properties) {
		this.hosts = Set.copyOf(properties.hosts());
	}
	
	public boolean isAdminRequest(HttpServletRequest request) {
		String host = request.getServerName();
		return host != null && hosts.contains(host.toLowerCase(Locale.ROOT));
	}
	
}
