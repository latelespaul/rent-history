package com.rentrix.rentrixserver.location;

/**
 * State of an area, society, tower or wing (§5.17, §5.18). {@code PENDING} rows were proposed by a landlord in a
 * property draft; until the listing is approved, lists show them only to the proposer and admins, and reads by id
 * also to a landlord with a home on them (D-109);
 * {@code HIDDEN} rows are no longer offered for new placements but keep their properties readable.
 */
public enum LocationStatus {
	ACTIVE,
	PENDING,
	HIDDEN
}
