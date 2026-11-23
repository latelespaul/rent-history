package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;

import java.time.LocalDate;

/**
 * D-39 review window of a confirmed tenancy: it opens once the tenancy has been {@code ACTIVE} for
 * {@value #OPENS_AFTER_DAYS} days or when it ends, whichever is first, and never closes (open model, firstmate
 * 2026-11-23).
 */
public final class ReviewWindow {
	
	public static final int OPENS_AFTER_DAYS = 60;
	
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
	
	public static boolean isOpen(Tenancy tenancy, LocalDate today) {
		return confirmed(tenancy) && !today.isBefore(opensOn(tenancy));
	}
	
}
