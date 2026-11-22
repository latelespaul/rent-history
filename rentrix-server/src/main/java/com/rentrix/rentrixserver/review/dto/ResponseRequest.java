package com.rentrix.rentrixserver.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST / PATCH /reviews/{id}/response}: the subject's one public reply, up to 1000 characters (§5.7). */
public record ResponseRequest(@NotNull @Size(max = 2000) String body) {
}
