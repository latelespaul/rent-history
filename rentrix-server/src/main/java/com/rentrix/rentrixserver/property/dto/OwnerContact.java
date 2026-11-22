package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.property.Property;

/**
 * The owner of a tenant-added home as that tenant named them (D-138): stored for a later claim and invite, private to
 * the tenant who entered it and admins (D-139). Absent fields were not given.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OwnerContact(String name, String email, String phone) {
	
	/** The property's stored owner details, or {@code null} when none were given. */
	public static OwnerContact of(Property property) {
		if (property.getOwnerName() == null && property.getOwnerEmail() == null && property.getOwnerPhone() == null) {
			return null;
		}
		return new OwnerContact(property.getOwnerName(), property.getOwnerEmail(), property.getOwnerPhone());
	}
	
}
