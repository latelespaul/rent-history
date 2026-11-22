package com.rentrix.rentrixserver.tenancy.dto;

/** Input bounds for tenancies and rent records (integer rupees, §12.1). */
public final class TenancyLimits {
	
	public static final long MAX_RENT_INR = 10_000_000;
	public static final long MAX_DEPOSIT_INR = 100_000_000;
	public static final int MAX_REASON = 500;
	
	private TenancyLimits() {
	}
	
}
