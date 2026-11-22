package com.rentrix.rentrixserver.user.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.identity.Badges;
import com.rentrix.rentrixserver.rating.RatingDetail;

import java.time.YearMonth;
import java.util.UUID;

/**
 * {@code GET /users/{id}}: the parts of a person's profile the caller may see. The landlord part (rating as landlord,
 * listed homes) is there for every signed-in caller when the person is a landlord (D-55); the tenant part (rating as
 * tenant) only under D-54. The two reputations are never mixed (D-18). Only the yes/no badges are shown (D-100);
 * never an email, phone or which ID method was used (D-57). PLAN B16 adds {@code homesRating}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileView(
	UUID id,
	String shortName,
	YearMonth memberSince,
	Badges badges,
	RatingDetail landlordRating,
	Long listedHomes,
	RatingDetail tenantRating
) {
}
