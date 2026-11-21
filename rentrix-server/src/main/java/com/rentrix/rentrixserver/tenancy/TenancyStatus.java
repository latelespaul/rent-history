package com.rentrix.rentrixserver.tenancy;

/** Tenancy verification lifecycle (§6). */
public enum TenancyStatus {
	PENDING_CONFIRMATION, ACTIVE, ENDED, DECLINED, EXPIRED, CANCELLED, VOIDED
}
