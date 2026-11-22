package com.rentrix.rentrixserver.property;

import java.util.Set;

/**
 * Listing state (D-92): {@code DRAFT} → (owner submits) {@code PENDING_REVIEW} → (admin) {@code PUBLISHED} or
 * {@code REJECTED} → (owner edits and submits again) {@code PENDING_REVIEW}; {@code PUBLISHED} → {@code ARCHIVED}.
 * Only {@code PUBLISHED} rows appear in search; {@code ARCHIVED} rows stay readable by direct link (D-21).
 */
public enum PropertyStatus {
	DRAFT, PENDING_REVIEW, REJECTED, PUBLISHED, ARCHIVED;
	
	/** States the owner may submit for listing review. */
	public static final Set<PropertyStatus> SUBMITTABLE = Set.of(DRAFT, REJECTED);
	
	/** Was published at some point, so anyone may open it by link (D-21). */
	public boolean isPublic() {
		return this == PUBLISHED || this == ARCHIVED;
	}
	
	/** Location fields (city, locality, building, street, pincode) can still change (D-92). */
	public boolean locationEditable() {
		return this == DRAFT || this == REJECTED;
	}
	
}
