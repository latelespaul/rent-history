package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;

/**
 * D-39 review window of a confirmed tenancy, changed by the open-by-default model (D-132) and the open model
 * (firstmate 2026-11-23): it is open from the moment the tenancy is {@code ACTIVE}, current tenants too, and never
 * closes, so a home left years ago can still be reviewed.
 */
public final class ReviewWindow {
	
	private ReviewWindow() {
	}
	
	public static boolean confirmed(Tenancy tenancy) {
		return tenancy.getStatus() == TenancyStatus.ACTIVE || tenancy.getStatus() == TenancyStatus.ENDED;
	}
	
}
