package com.rentrix.rentrixserver.review;

import java.util.Arrays;
import java.util.List;

/**
 * The parameters of the review form (D-157), each rated 1–5 stars and each optional. Every parameter belongs to one
 * direction: the tenant rates the home on the first nine and the landlord on the next five; the owner rates the
 * tenant on the last five (LT-6).
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
	BEHAVIOUR(ReviewDirection.TENANT_TO_LANDLORD),
	RENT_ON_TIME(ReviewDirection.LANDLORD_TO_TENANT),
	CARE_OF_HOME(ReviewDirection.LANDLORD_TO_TENANT),
	COMMUNICATION(ReviewDirection.LANDLORD_TO_TENANT),
	HOUSE_RULES(ReviewDirection.LANDLORD_TO_TENANT),
	MOVE_OUT(ReviewDirection.LANDLORD_TO_TENANT);
	
	private final ReviewDirection direction;
	
	ReviewCriterion(ReviewDirection direction) {
		this.direction = direction;
	}
	
	public ReviewDirection direction() {
		return direction;
	}
	
	/** The parameters of one direction, in form order. */
	public static List<ReviewCriterion> of(ReviewDirection direction) {
		return Arrays.stream(values()).filter(c -> c.direction == direction).toList();
	}
	
}
