package com.rentrix.rentrixserver.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * The admin console's own host (D-19, §13.3). {@code hosts} from {@code ADMIN_HOSTS} (comma list of host names, such
 * as {@code admin.doorwayliving.in}): only requests whose host is listed may sign in as an admin or call
 * {@code /admin/**}. {@code emails} from {@code ADMIN_CONSOLE_EMAILS} (comma list of verified Google emails): who may
 * sign in there. Both empty means no admin console.
 */
@ConfigurationProperties(prefix = "app.admin-console")
public record AdminConsoleProperties(List<String> hosts, List<String> emails) {
	
	public AdminConsoleProperties {
		hosts = normalise(hosts);
		emails = normalise(emails);
	}
	
	private static List<String> normalise(List<String> values) {
		return values == null ? List.of() : values.stream()
			.map(String::trim).filter(StringUtils::hasText).map(v -> v.toLowerCase(Locale.ROOT)).distinct().toList();
	}
	
}
