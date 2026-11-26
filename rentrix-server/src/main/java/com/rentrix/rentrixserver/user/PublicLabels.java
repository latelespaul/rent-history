package com.rentrix.rentrixserver.user;

import java.time.LocalDate;

/**
 * How a person shows to other people (D-168, D-169): never by name and never by a date, only by an anonymous label.
 * A tenant is "Verified tenant" when the stay is verified, else "Tenant"; a landlord is "Owner". No year, stay date
 * or "currently renting" goes into a label, so the reviewed party cannot tell which of their tenants wrote a review.
 * The name itself goes only to the person, admins and, on tenancy pages, the other party of that tenancy.
 */
public final class PublicLabels {

	public static final String OWNER = "Owner";
	public static final String TENANT = "Tenant";
	public static final String VERIFIED_TENANT = "Verified tenant";

	private PublicLabels() {
	}

	/** "Verified tenant" for a verified stay, else plain "Tenant" (D-169). */
	public static String tenant(boolean verifiedStay) {
		return verifiedStay ? VERIFIED_TENANT : TENANT;
	}

	/**
	 * The coarse "Stayed 1+ yr" badge (D-169): the stay from {@code start} lasted a year or more by {@code asOf} (the
	 * review's submit day) or by its {@code end}, whichever is first. Fixed at submit time, so it never flips later
	 * and gives away when a current stay began.
	 */
	public static boolean longStay(LocalDate start, LocalDate end, LocalDate asOf) {
		if (start == null || asOf == null) {
			return false;
		}
		LocalDate until = end != null && end.isBefore(asOf) ? end : asOf;
		return !start.plusYears(1).isAfter(until);
	}

}
