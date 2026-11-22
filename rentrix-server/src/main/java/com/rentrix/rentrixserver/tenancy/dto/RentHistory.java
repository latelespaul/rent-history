package com.rentrix.rentrixserver.tenancy.dto;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /properties/{id}/rent-history} (D-26): confirmed rent of the property's confirmed tenancies, months
 * only and without tenant identity (D-57). Rows are ordered by {@code fromMonth}, oldest first.
 */
public record RentHistory(UUID propertyId, List<Row> rows) {
	
	/**
	 * {@code fromMonth} and {@code toMonth} are {@code YYYY-MM}; {@code toMonth} is {@code null} while the rent is
	 * current. {@code share} marks rows of a {@code ROOM} or {@code PG} property, where flatmate tenancies overlap
	 * and each row is one room or share. {@code monthlyRentInr} is absent where the tenancy's rent is masked
	 * (D-139). {@code confirmed} is false for a tenant's own rent on an unclaimed home, which nobody confirmed (D-138).
	 */
	public record Row(String fromMonth, String toMonth, Long monthlyRentInr, Long maintenanceInr, boolean share,
		boolean confirmed) {
	}
	
}
