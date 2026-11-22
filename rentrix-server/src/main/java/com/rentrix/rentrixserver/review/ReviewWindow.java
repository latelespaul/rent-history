package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;

import java.time.LocalDate;

/**
 * D-39 review window of a confirmed tenancy: it opens once the tenancy has been {@code ACTIVE} for
 * {@value #OPENS_AFTER_DAYS} days or when it ends, whichever is first, and closes {@value #CLOSES_AFTER_DAYS} days
 * after the end date. While the tenancy is current it stays open.
 */
public final class ReviewWindow {
	
	public static final int OPENS_AFTER_DAYS = 60;
	public static final int CLOSES_AFTER_DAYS = 180;
	
	private ReviewWindow() {
	}
	
	public static boolean confirmed(Tenancy tenancy) {
		return tenancy.getStatus() == TenancyStatus.ACTIVE || tenancy.getStatus() == TenancyStatus.ENDED;
	}
	
	public static LocalDate opensOn(Tenancy tenancy) {
		LocalDate afterSixtyDays = tenancy.getStartDate().plusDays(OPENS_AFTER_DAYS);
		LocalDate end = tenancy.getStatus() == TenancyStatus.ENDED ? tenancy.getEndDate() : null;
		return end != null && end.isBefore(afterSixtyDays) ? end : afterSixtyDays;
	}
	
	/** The last day reviews can be written, or {@code null} while the tenancy is current. */
	public static LocalDate closesOn(Tenancy tenancy) {
		return tenancy.getStatus() == TenancyStatus.ENDED && tenancy.getEndDate() != null
			? tenancy.getEndDate().plusDays(CLOSES_AFTER_DAYS) : null;
	}
	
	public static boolean isOpen(Tenancy tenancy, LocalDate today) {
		LocalDate closes = closesOn(tenancy);
		return confirmed(tenancy) && !today.isBefore(opensOn(tenancy)) && (closes == null || !today.isAfter(closes));
	}
	
}
