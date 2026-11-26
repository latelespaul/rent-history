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
	 * The coarse stay length (D-171): how long the stay from {@code start} had lasted by {@code asOf} (the review's
	 * submit day) or by its {@code end}, whichever is first. Fixed at submit time, so it never moves on later and gives
	 * away when a current stay began. Null without a start or a submit day.
	 */
	public static StayLength stayLength(LocalDate start, LocalDate end, LocalDate asOf) {
		if (start == null || asOf == null) {
			return null;
		}
		LocalDate until = end != null && end.isBefore(asOf) ? end : asOf;
		if (start.plusYears(1).isAfter(until)) {
			return StayLength.UNDER_ONE_YEAR;
		}
		return start.plusYears(2).isAfter(until) ? StayLength.ONE_TO_TWO_YEARS : StayLength.TWO_PLUS_YEARS;
	}

}
