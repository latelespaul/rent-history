package com.rentrix.rentrixserver.user;

/**
 * The coarse length of a tenant author's stay on a review (D-171): "Stayed &lt; 1 yr", "Stayed 1-2 yrs" or "Stayed 2+
 * yrs". Never a month or a year, so the reviewed party cannot tell when the stay began or ended.
 */
public enum StayLength {
	UNDER_ONE_YEAR,
	ONE_TO_TWO_YEARS,
	TWO_PLUS_YEARS
}
