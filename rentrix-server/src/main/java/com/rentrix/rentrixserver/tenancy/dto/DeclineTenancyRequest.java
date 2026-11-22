package com.rentrix.rentrixserver.tenancy.dto;

import jakarta.validation.constraints.Size;

/** {@code POST /tenancies/{id}/decline}: optional reason shown to the initiator (D-34). */
public record DeclineTenancyRequest(@Size(max = TenancyLimits.MAX_REASON) String reason) {
}
