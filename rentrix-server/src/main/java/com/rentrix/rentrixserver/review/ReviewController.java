package com.rentrix.rentrixserver.review;

import com.rentrix.rentrixserver.common.idempotency.Idempotent;
import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.rating.RatingDetail;
import com.rentrix.rentrixserver.review.dto.CreateReviewRequest;
import com.rentrix.rentrixserver.review.dto.ReviewFormRequest;
import com.rentrix.rentrixserver.review.dto.ReviewFormView;
import com.rentrix.rentrixserver.review.dto.ResponseRequest;
import com.rentrix.rentrixserver.review.dto.ReviewTask;
import com.rentrix.rentrixserver.review.dto.ReviewView;
import com.rentrix.rentrixserver.review.dto.UpdateReviewRequest;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Review endpoints (§12.2, §7). Writing needs a signed-in, approved party (D-91); reading needs a signed-in caller
 * and follows the subject's visibility (§10), except {@code GET /properties/{id}/rating} and
 * {@code GET /properties/{id}/reviews}, which are public (D-161).
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {
	
	/**
	 * {@code sort=rating} with {@code ,asc|desc}; the highest rating first by default. No "newest": the posting order
	 * of one home's reviews would tell its landlord which tenant wrote which (D-169).
	 */
	public static final Map<String, String> SORTS = Map.of("rating", "ratingOverall");
	public static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "ratingOverall");
	static final Map<String, String> WRITTEN_SORTS = Map.of("newest", "submittedAt");
	static final Sort DEFAULT_WRITTEN_SORT = Sort.by(Sort.Direction.DESC, "submittedAt");
	
	private final ReviewService reviews;
	
	@PostMapping("/tenancies/{id}/reviews")
	@Idempotent
	public ResponseEntity<ReviewView> create(@PathVariable UUID id, @Valid @RequestBody CreateReviewRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		ReviewView created = reviews.create(id, request, caller);
		return ResponseEntity.created(URI.create("/api/v1/reviews/" + created.id())).body(created);
	}
	
	/** D-157: the tenant's review form, the home and the landlord reviews saved together; D-170: the owner's. */
	@PostMapping("/tenancies/{id}/review-form")
	@Idempotent
	public ResponseEntity<ReviewFormView> form(@PathVariable UUID id, @RequestBody ReviewFormRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		ReviewFormView written = reviews.form(id, request, caller);
		ReviewView first = written.home() != null ? written.home()
			: written.landlord() != null ? written.landlord() : written.tenant();
		return ResponseEntity.created(URI.create("/api/v1/reviews/" + first.id())).body(written);
	}
	
	@GetMapping("/reviews/{id}")
	public ReviewView get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return reviews.get(id, caller);
	}
	
	@PatchMapping("/reviews/{id}")
	public ReviewView update(@PathVariable UUID id, @Valid @RequestBody UpdateReviewRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return reviews.update(id, request, caller);
	}
	
	@DeleteMapping("/reviews/{id}")
	public ResponseEntity<Void> withdraw(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		reviews.withdraw(id, caller);
		return ResponseEntity.noContent().build();
	}
	
	@PostMapping("/reviews/{id}/response")
	public ResponseEntity<ReviewView> respond(@PathVariable UUID id, @Valid @RequestBody ResponseRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		ReviewView review = reviews.respond(id, request.body(), caller);
		return ResponseEntity.created(URI.create("/api/v1/reviews/" + id)).body(review);
	}
	
	@PatchMapping("/reviews/{id}/response")
	public ReviewView updateResponse(@PathVariable UUID id, @Valid @RequestBody ResponseRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return reviews.updateResponse(id, request.body(), caller);
	}
	
	/** {@code GET /properties/{id}/reviews?page=&size=&sort=rating}: public, full text (D-161). */
	@GetMapping("/properties/{id}/reviews")
	public PageResponse<ReviewView> forProperty(@PathVariable UUID id, @RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return reviews.forProperty(id, caller, PageRequests.of(page, size, sort, SORTS, DEFAULT_SORT));
	}
	
	/** {@code GET /properties/{id}/rating}: public aggregate (D-52, D-56). */
	@GetMapping("/properties/{id}/rating")
	public RatingDetail propertyRating(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return reviews.propertyRating(id, caller);
	}
	
	/** {@code GET /me/reviews/written?as=TENANT|LANDLORD}: both sides when {@code as} is absent (the mode switch). */
	@GetMapping("/me/reviews/written")
	public PageResponse<ReviewView> written(@RequestParam(required = false, name = "as") TenancyParty as,
		@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
		@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser caller) {
		return reviews.written(caller, as, PageRequests.of(page, size, sort, WRITTEN_SORTS, DEFAULT_WRITTEN_SORT));
	}
	
	/** {@code GET /me/reviews/received?as=LANDLORD|TENANT}: both capacities when {@code as} is absent. */
	@GetMapping("/me/reviews/received")
	public PageResponse<ReviewView> received(@RequestParam(required = false, name = "as") TenancyParty as,
		@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return reviews.received(caller, as, PageRequests.of(page, size, null, WRITTEN_SORTS, DEFAULT_WRITTEN_SORT));
	}
	
	@GetMapping("/me/review-tasks")
	public List<ReviewTask> tasks(@AuthenticationPrincipal AuthenticatedUser caller) {
		return reviews.tasks(caller);
	}
	
}
