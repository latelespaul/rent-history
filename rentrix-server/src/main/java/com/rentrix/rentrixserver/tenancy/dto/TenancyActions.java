package com.rentrix.rentrixserver.tenancy.dto;

/**
 * What the caller may do next on this tenancy, by role and state only (§6, §11). The approval gate (D-91) is
 * checked on the write itself and answers {@code 403 approval_required}. {@code edit} and {@code delete} are for the
 * party who added it (D-156); delete is refused on the write when the other side confirmed it or reviewed it.
 */
public record TenancyActions(boolean confirm, boolean decline, boolean cancel, boolean end, boolean addRentRecord,
	boolean edit, boolean delete) {
	
	public static final TenancyActions NONE = new TenancyActions(false, false, false, false, false, false, false);
	
}
