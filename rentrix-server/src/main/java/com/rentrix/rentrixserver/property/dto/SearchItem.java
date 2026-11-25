package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row of {@code GET /properties/grouped} (D-166): a {@code SOCIETY} with 2+ matching homes, or a single
 * {@code HOME}; exactly one of {@code society} and {@code home} is set.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchItem(Kind kind, PropertyCard home, SocietyGroup society) {
	
	public enum Kind {
		HOME, SOCIETY
	}
	
	public static SearchItem home(PropertyCard card) {
		return new SearchItem(Kind.HOME, card, null);
	}
	
	public static SearchItem society(SocietyGroup group) {
		return new SearchItem(Kind.SOCIETY, null, group);
	}
	
}
