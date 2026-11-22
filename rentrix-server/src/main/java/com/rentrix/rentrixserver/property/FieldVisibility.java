package com.rentrix.rentrixserver.property;

/**
 * Who sees one sensitive field (D-140): the flat number, the exact rent, the owner's contact. Stored per row, so a
 * field can be masked later by changing data and code, never the schema.
 */
public enum FieldVisibility {
	
	/** Everyone who may see the record sees the field. */
	OPEN,
	
	/** Hidden from everyone but the owner of the record and admins (today's D-57 rule for flat numbers). */
	MASKED,
	
	/** Only the person who entered it and admins. Owner contact details are always private. */
	PRIVATE
	
}
