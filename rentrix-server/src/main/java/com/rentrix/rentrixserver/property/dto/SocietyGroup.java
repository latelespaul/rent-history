package com.rentrix.rentrixserver.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.location.dto.AreaRef;

import java.util.UUID;

/**
 * One society as search shows it when 2+ of its homes match (D-166), and the society page's summary
 * ({@code GET /societies/{id}/summary}). {@code flats} counts the homes it stands for; {@code rating} pools their
 * stored home aggregates, so it counts only verified reviews (D-163) and shows an average from 3 on (D-52);
 * {@code listedReviews} counts every published home review, verified or not. {@code rentRange} is for signed-in
 * callers only, like rent history (D-56), and uses only rents open to everyone (D-140).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocietyGroup(
	UUID id,
	String name,
	AreaRef area,
	CityRef city,
	boolean pending,
	int flats,
	int listedReviews,
	RatingSummary rating,
	RentRange rentRange
) {
	
	/** The lowest and highest latest monthly rent of the homes, in rupees. */
	public record RentRange(long min, long max) {
	}
	
}
