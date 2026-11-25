package com.rentrix.rentrixserver.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rentrix.rentrixserver.identity.TrustBadges;
import com.rentrix.rentrixserver.property.dto.CityRef;
import com.rentrix.rentrixserver.review.ReviewCriterion;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.verification.VerificationState;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/**
 * A review as the caller may see it. The author line is an anonymous label, the three badges and the tenancy period
 * in months (D-168, D-57); the tenancy itself is never linked. No person's name leaves the server here except to that
 * person and admins ({@code shortName} on {@code author}, {@code subject} and {@code response}); {@code author.id}
 * is for admins only. {@code body} is absent while a personal-data
 * report hides the text ({@code textHidden}, D-50). {@code editUntil} and {@code hiddenReason} are for the author
 * and admins only. A review about the caller that still awaits reveal is listed as a bare {@code AWAITING_REVEAL}
 * entry, with no ratings, text or author (D-46). {@code verification} is the green tick (D-134): {@code UNVERIFIED}
 * ("Not verified yet") until an admin decides, then {@code VERIFIED} or {@code REJECTED}. A form review (D-157) has
 * {@code formReview} true and its comment is optional on edit; it has {@code criteria}, the parameters the author rated in form order, and may have {@code wouldRecommend}, {@code pros}
 * and {@code cons}; {@code body} is absent when it has no comment. Pros and cons are hidden with the text (D-50).
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
	Boolean formReview,
	Map<ReviewCriterion, Integer> criteria,
	Boolean wouldRecommend,
	String body,
	String pros,
	String cons,
	Boolean textHidden,
	Author author,
	Person subject,
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
	
	/**
	 * {@code label}: "Tenant, 2024" (the stay's start year) or "Owner" (D-168); {@code shortName} only for the author
	 * and admins; {@code badges} the three verification badges, {@code agreement} from this review's tenancy.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Author(UUID id, String label, String shortName, TrustBadges badges) {
	}
	
	/** The reviewed person: their label (D-168), and {@code shortName} only for that person and admins. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Person(UUID id, String label, String shortName) {
	}
	
	/** The reviewed home; no unit label (D-57). */
	public record PropertyRef(UUID id, String buildingName, String locality, CityRef city) {
	}
	
	/** The tenancy period in months; {@code to} is absent while the tenancy is current. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Period(YearMonth from, YearMonth to) {
	}
	
	/** {@code authorLabel} as on {@link Person}; {@code authorShortName} only for the responder and admins (D-168). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Response(String body, String authorLabel, String authorShortName, Instant createdAt,
		Instant editUntil) {
	}
	
	/** What the caller may do on this review now; the approval gate (D-91) is checked on the write itself. */
	public record Actions(boolean edit, boolean withdraw, boolean respond, boolean editResponse) {
		
		public static final Actions NONE = new Actions(false, false, false, false);
		
	}
	
}
