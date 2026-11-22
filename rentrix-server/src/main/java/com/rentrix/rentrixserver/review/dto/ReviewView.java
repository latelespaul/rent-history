package com.rentrix.rentrixserver.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.identity.Badges;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.tenancy.dto.PartyRef;
import com.rentrix.rentrixserver.verification.VerificationState;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * A review as the caller may see it. The author line is the short name, the badges (D-100) and the verified
 * tenancy period in months (D-28, D-57); the tenancy itself is never linked. {@code author.id} is present only for
 * landlord authors, whose profiles signed-in users can open (D-55). {@code body} is absent while a personal-data
 * report hides the text ({@code textHidden}, D-50). {@code editUntil} and {@code hiddenReason} are for the author
 * and admins only. A review about the caller that still awaits reveal is listed as a bare {@code AWAITING_REVEAL}
 * entry, with no ratings, text or author (D-46). {@code verification} is the green tick (D-134): {@code UNVERIFIED}
 * ("Not verified yet") until an admin decides, then {@code VERIFIED} or {@code REJECTED}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewView(
	UUID id,
	ReviewDirection direction,
	ReviewStatus status,
	Integer ratingOverall,
	Integer ratingA,
	Integer ratingB,
	Integer ratingC,
	String body,
	Boolean textHidden,
	Author author,
	PartyRef subject,
	PropertyRef property,
	Period period,
	Instant submittedAt,
	Instant publishedAt,
	Instant editedAt,
	Instant editUntil,
	String hiddenReason,
	Response response,
	Actions actions,
	VerificationState verification
) {
	
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Author(UUID id, String shortName, Badges badges) {
	}
	
	/** The reviewed home; no unit label (D-57). */
	public record PropertyRef(UUID id, String buildingName, String locality, CityRef city) {
	}
	
	/** The tenancy period in months; {@code to} is absent while the tenancy is current. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Period(YearMonth from, YearMonth to) {
	}
	
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Response(String body, String authorShortName, Instant createdAt, Instant editUntil) {
	}
	
	/** What the caller may do on this review now; the approval gate (D-91) is checked on the write itself. */
	public record Actions(boolean edit, boolean withdraw, boolean respond, boolean editResponse) {
		
		public static final Actions NONE = new Actions(false, false, false, false);
		
	}
	
}
