package com.rentrix.rentrixserver.location;

/**
 * State of an area, society, tower or wing (§5.17, §5.18). {@code PENDING} rows were proposed by a landlord in a
 * property draft and are seen only by the proposer and admins until the listing is approved (D-109);
 * {@code HIDDEN} rows are no longer offered for new placements but keep their properties readable.
 */
public enum LocationStatus {
	ACTIVE,
	PENDING,
	HIDDEN
}
