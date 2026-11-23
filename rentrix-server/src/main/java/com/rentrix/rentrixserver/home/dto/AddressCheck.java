package com.rentrix.rentrixserver.home.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code GET /me/homes/address-check}: step 1 of "Add your home" checked on its own Next (firstmate 2026-11-23 14:50).
 * {@code pincodeError} blocks (the PIN code is not in the city); {@code pincodeWarning} only warns (the picked area
 * does not list it; the user may keep it). {@code areaPincodes} are the picked area's PIN codes, for the autofill.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressCheck(String pincodeError, String pincodeWarning, List<String> areaPincodes) {
}
