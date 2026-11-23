package com.rentrix.rentrixserver.agreement;

import java.util.Locale;

/**
 * The government-checkable fact on the agreement, by the home's state (D-124): the Maharashtra leave and licence
 * registration, the Karnataka e-stamp certificate, another stamp paper, or none (no number on the agreement).
 */
public enum AnchorKind {
	MH_LEAVE_LICENCE, KA_ESTAMP, OTHER_STAMP, NONE;
	
	/** The kind for a home in {@code state} whose agreement shows a stamp or registration number. */
	public static AnchorKind forState(String state) {
		String s = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
		return switch (s) {
			case "maharashtra" -> MH_LEAVE_LICENCE;
			case "karnataka" -> KA_ESTAMP;
			default -> OTHER_STAMP;
		};
	}
	
}
