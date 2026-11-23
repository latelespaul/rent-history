package com.rentrix.rentrixserver.report.dto;

import com.rentrix.rentrixserver.edit.EditTarget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** {@code POST /me/change-requests} (D-156): what the creator wants the team to change on their own item. */
public record ChangeRequest(
	@NotNull EditTarget targetType,
	@NotNull UUID targetId,
	@NotBlank @Size(max = 1000) String note
) {
}
