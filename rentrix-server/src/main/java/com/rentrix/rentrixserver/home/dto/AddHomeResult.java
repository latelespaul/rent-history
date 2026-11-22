package com.rentrix.rentrixserver.home.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.property.dto.PropertyDetail;
import com.rentrix.rentrixserver.tenancy.dto.TenancyView;

/**
 * The answer of {@code POST /me/homes}: the home, the caller's tenancy on it ({@code RENT} only), and a fresh access
 * token when the step gave the caller a new role (a tenant who adds a home they own becomes a landlord too, D-16).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddHomeResult(PropertyDetail property, TenancyView tenancy, String accessToken) {
}
