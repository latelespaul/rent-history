package com.rentrix.rentrixserver.user.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.identity.TrustBadges;
import com.rentrix.rentrixserver.rating.RatingDetail;

import java.time.YearMonth;
import java.util.UUID;

/**
 * {@code GET /users/{id}}: the parts of a person's profile the caller may see. The landlord part (rating as landlord,
 * listed homes) is there for every signed-in caller when the person is a landlord (D-55); the tenant part (rating as
 * tenant, with the five parameter averages and "would rent to them again", landlord-adds-tenant S2) only to the owners
 * D-54 allows and admins (D-175): the tenant sees their own profile without it. The two reputations are never mixed (D-18). The person shows as {@code label}, "Owner" or
 * "Tenant", never by name; {@code shortName} only for the person and admins (D-168). Only the three yes/no badges are
 * shown (D-168); never an email, phone or which ID method was used (D-57). PLAN B16 adds {@code homesRating}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileView(
	UUID id,
	String label,
	String shortName,
	YearMonth memberSince,
	TrustBadges badges,
	RatingDetail landlordRating,
	Long listedHomes,
	RatingDetail tenantRating
) {
}
