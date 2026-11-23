package com.rentrix.rentrixserver.property;

/**
 * How big a home is, the way Indian listings say it (captain 2026-11-23 17:18): 1 RK, 1-4 BHK, 5+ BHK, studio, or
 * {@code OTHER} with free text. It also fills {@code bedrooms} when that is not given, so the bedrooms search and
 * every older screen keep working.
 */
public enum HomeSize {
	RK1(0), BHK1(1), BHK2(2), BHK3(3), BHK4(4), BHK5_PLUS(5), STUDIO(0), OTHER(null);
	
	private final Integer bedrooms;
	
	HomeSize(Integer bedrooms) {
		this.bedrooms = bedrooms;
	}
	
	/**
	 * The size a bedroom count says, for callers that send only {@code bedrooms} (the landlord form): a studio is
	 * {@code STUDIO}, 1-4 are BHK, 5 or more {@code BHK5_PLUS}; 0 bedrooms of another type stays unknown.
	 */
	public static HomeSize fromBedrooms(PropertyType type, Integer bedrooms) {
		if (type == PropertyType.STUDIO) {
			return STUDIO;
		}
		if (bedrooms == null || bedrooms < 1) {
			return null;
		}
		return switch (bedrooms) {
			case 1 -> BHK1;
			case 2 -> BHK2;
			case 3 -> BHK3;
			case 4 -> BHK4;
			default -> BHK5_PLUS;
		};
	}
	
	/** The bedroom count this size stands for, or null for {@code OTHER}. */
	public Integer bedrooms() {
		return bedrooms;
	}
	
}
