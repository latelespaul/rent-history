package com.rentrix.rentrixserver.tenancy.dto;

import java.util.UUID;

/** The other side of a tenancy as its parties see it: public id and short name only (D-57). */
public record PartyRef(UUID id, String shortName) {
}
