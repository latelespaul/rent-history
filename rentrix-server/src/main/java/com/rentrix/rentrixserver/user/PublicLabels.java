package com.rentrix.rentrixserver.user;

import java.time.LocalDate;

/**
 * How a person shows to other people (D-168): never by name, only by an anonymous label. A tenant is "Tenant" plus
 * the start year of the stay the item comes from ("Tenant, 2024"); a landlord is "Owner". The name itself goes only
 * to the person, admins and, on tenancy pages, the other party of that tenancy.
 */
public final class PublicLabels {
	
	public static final String OWNER = "Owner";
	public static final String TENANT = "Tenant";
	
	private PublicLabels() {
	}
	
	/** "Tenant, 2024" for a stay that started in 2024; plain "Tenant" without a start date. */
	public static String tenant(LocalDate stayStart) {
		return stayStart == null ? TENANT : TENANT + ", " + stayStart.getYear();
	}
	
}
