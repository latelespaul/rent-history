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
 * A review as the caller may see it. The author line is an anonymous label and the three badges (D-168); the tenancy
 * itself is never linked. Nothing on it lets the reviewed party work out which tenant wrote it (D-169): the stay
 * ({@code period}) and the exact times ({@code submittedAt}, {@code publishedAt}, {@code editedAt}) go only to the
 * author and admins; everyone else gets {@code publishedMonth} (month and year, never the day or time) and
 * {@code edited} (yes/no). The review {@code id} is random, not a time-ordered UUIDv7. No person's name leaves the
 * server here except to that person and admins ({@code shortName} on {@code author}, {@code subject} and
 * {@code response}); {@code author.id} is for admins only. {@code body} is absent while a personal-data
 * report hides the text ({@code textHidden}, D-50). {@code editUntil} and {@code hiddenReason} are for the author
 * and admins only. A review about the caller that still awaits reveal is listed as a bare {@code AWAITING_REVEAL}
 * entry, with no ratings, text, author or stay (D-46). {@code verification} is the green tick (D-134): {@code UNVERIFIED}
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
	YearMonth publishedMonth,
	Boolean edited,
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
	 * {@code label}: "Verified tenant", "Tenant" or "Owner", never a year (D-168, D-169); {@code longStay} true for the
	 * coarse "Stayed 1+ yr" badge, absent otherwise; {@code shortName} only for the author and admins; {@code badges}
	 * the three verification badges, {@code agreement} from this review's tenancy.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Author(UUID id, String label, Boolean longStay, String shortName, TrustBadges badges) {
	}
	
	/** The reviewed person: their label (D-168), and {@code shortName} only for that person and admins. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Person(UUID id, String label, String shortName) {
	}
	
	/** The reviewed home; no unit label (D-57). */
	public record PropertyRef(UUID id, String buildingName, String locality, CityRef city) {
	}
	
	/**
	 * The tenancy period in months; {@code to} is absent while the tenancy is current. Only for the author and admins
	 * (D-169).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Period(YearMonth from, YearMonth to) {
	}
	
	/**
	 * {@code authorLabel} as on {@link Person}; {@code authorShortName} only for the responder and admins (D-168);
	 * {@code createdAt} only for the responder and admins (D-169).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Response(String body, String authorLabel, String authorShortName, Instant createdAt,
		Instant editUntil) {
	}
	
	/** What the caller may do on this review now; the approval gate (D-91) is checked on the write itself. */
	public record Actions(boolean edit, boolean withdraw, boolean respond, boolean editResponse) {
		
		public static final Actions NONE = new Actions(false, false, false, false);
		
	}
	
}
