package com.rentrix.rentrixserver.agreement.dto;

import java.time.Instant;

/** A 5-minute signed link to the decrypted file (D-30, D-95, D-131). */
public record SignedUrlView(String url, Instant expiresAt) {
}
