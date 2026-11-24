package com.rentrix.rentrixserver.review;

import java.util.Arrays;
import java.util.List;

/**
 * The parameters of the review form (D-157), each rated 1–5 stars and each optional. Every parameter belongs to one
 * direction: the tenant rates the home on the first nine and the landlord on the last five.
 */
public enum ReviewCriterion {
	
	WATER_SUPPLY(ReviewDirection.TENANT_TO_PROPERTY),
	POWER_BACKUP(ReviewDirection.TENANT_TO_PROPERTY),
	MAINTENANCE(ReviewDirection.TENANT_TO_PROPERTY),
	CLEANLINESS(ReviewDirection.TENANT_TO_PROPERTY),
	SECURITY(ReviewDirection.TENANT_TO_PROPERTY),
	PARKING(ReviewDirection.TENANT_TO_PROPERTY),
	NOISE(ReviewDirection.TENANT_TO_PROPERTY),
	NEIGHBOURHOOD(ReviewDirection.TENANT_TO_PROPERTY),
	VALUE_FOR_RENT(ReviewDirection.TENANT_TO_PROPERTY),
	RESPONSIVENESS(ReviewDirection.TENANT_TO_LANDLORD),
	DEPOSIT_RETURN(ReviewDirection.TENANT_TO_LANDLORD),
	TRANSPARENCY(ReviewDirection.TENANT_TO_LANDLORD),
	PRIVACY(ReviewDirection.TENANT_TO_LANDLORD),
	BEHAVIOUR(ReviewDirection.TENANT_TO_LANDLORD);
	
	private final ReviewDirection direction;
	
	ReviewCriterion(ReviewDirection direction) {
		this.direction = direction;
	}
	
	public ReviewDirection direction() {
		return direction;
	}
	
	/** The parameters of one direction, in form order; none for {@code LANDLORD_TO_TENANT}. */
	public static List<ReviewCriterion> of(ReviewDirection direction) {
		return Arrays.stream(values()).filter(c -> c.direction == direction).toList();
	}
	
}
