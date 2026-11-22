package com.rentrix.rentrixserver.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /tenancy-invites/claim}: the token from the invite link, in the body so it stays out of URLs. */
public record ClaimInviteRequest(@NotBlank @Size(max = 100) String token) {
}
