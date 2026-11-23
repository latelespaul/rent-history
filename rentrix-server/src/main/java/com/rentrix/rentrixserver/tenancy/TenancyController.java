package com.rentrix.rentrixserver.tenancy;

import com.rentrix.rentrixserver.common.idempotency.Idempotent;
import com.rentrix.rentrixserver.common.web.PageRequests;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.dto.ClaimInviteRequest;
import com.rentrix.rentrixserver.tenancy.dto.CreateRentRecordRequest;
import com.rentrix.rentrixserver.tenancy.dto.CreateTenancyRequest;
import com.rentrix.rentrixserver.tenancy.dto.DeclineTenancyRequest;
import com.rentrix.rentrixserver.tenancy.dto.EndTenancyRequest;
import com.rentrix.rentrixserver.tenancy.dto.RentHistory;
import com.rentrix.rentrixserver.tenancy.dto.RentRecordView;
import com.rentrix.rentrixserver.tenancy.dto.TenancyView;
import com.rentrix.rentrixserver.tenancy.dto.UpdateRentRecordRequest;
import com.rentrix.rentrixserver.tenancy.dto.UpdateTenancyRequest;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tenancy and rent endpoints (§12.2, §6). All need a signed-in caller; tenancies and their rent records are
 * readable by their parties and admins only. The rent history of a property is open to any signed-in user (D-56).
 */
@RestController
@RequiredArgsConstructor
public class TenancyController {
	
	static final Map<String, String> MINE_SORTS = Map.of("newest", "createdAt", "start", "startDate",
		"updated", "updatedAt");
	static final Sort DEFAULT_MINE_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
	
	private final TenancyService tenancies;
	private final RentRecordService rentRecords;
	
	@PostMapping("/tenancies")
	@Idempotent
	public ResponseEntity<TenancyView> create(@Valid @RequestBody CreateTenancyRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		TenancyView created = tenancies.create(request, caller);
		return ResponseEntity.created(URI.create("/api/v1/tenancies/" + created.id())).body(created);
	}
	
	@GetMapping("/tenancies/{id}")
	public TenancyView get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return tenancies.get(id, caller);
	}
	
	/** The party who added it corrects the period, rent or deposit (D-156). */
	@PatchMapping("/tenancies/{id}")
	public TenancyView update(@PathVariable UUID id, @Valid @RequestBody UpdateTenancyRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return tenancies.update(id, request, caller);
	}
	
	/** The party who added it removes it (D-156). */
	@DeleteMapping("/tenancies/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		tenancies.delete(id, caller);
		return ResponseEntity.noContent().build();
	}
	
	@PostMapping("/tenancies/{id}/confirm")
	public TenancyView confirm(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return tenancies.confirm(id, caller);
	}
	
	@PostMapping("/tenancies/{id}/decline")
	public TenancyView decline(@PathVariable UUID id, @Valid @RequestBody(required = false) DeclineTenancyRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return tenancies.decline(id, request == null ? null : request.reason(), caller);
	}
	
	@PostMapping("/tenancies/{id}/cancel")
	public TenancyView cancel(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return tenancies.cancel(id, caller);
	}
	
	@PostMapping("/tenancies/{id}/end")
	public TenancyView end(@PathVariable UUID id, @Valid @RequestBody EndTenancyRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return tenancies.end(id, request, caller);
	}
	
	/** {@code POST /tenancy-invites/claim}: binds a landlord's email invite to the signed-in invitee (D-34). */
	@PostMapping("/tenancy-invites/claim")
	public TenancyView claim(@Valid @RequestBody ClaimInviteRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return tenancies.claim(request.token(), caller);
	}
	
	/** {@code GET /me/tenancies?role=LANDLORD|TENANT&status=ACTIVE,ENDED&page=&size=&sort=newest|start|updated}. */
	@GetMapping("/me/tenancies")
	public PageResponse<TenancyView> mine(@RequestParam(required = false) TenancyParty role,
		@RequestParam(required = false) List<TenancyStatus> status,
		@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
		@RequestParam(required = false) String sort, @AuthenticationPrincipal AuthenticatedUser caller) {
		Set<TenancyStatus> statuses = status == null || status.isEmpty() ? Set.of() : EnumSet.copyOf(status);
		return tenancies.mine(caller, role, statuses, PageRequests.of(page, size, sort, MINE_SORTS, DEFAULT_MINE_SORT));
	}
	
	@GetMapping("/tenancies/{id}/rent-records")
	public List<RentRecordView> rentRecords(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return rentRecords.list(id, caller);
	}
	
	@PostMapping("/tenancies/{id}/rent-records")
	public ResponseEntity<RentRecordView> addRentRecord(@PathVariable UUID id,
		@Valid @RequestBody CreateRentRecordRequest request, @AuthenticationPrincipal AuthenticatedUser caller) {
		RentRecordView created = rentRecords.add(id, request, caller);
		return ResponseEntity.created(URI.create("/api/v1/tenancies/" + id + "/rent-records")).body(created);
	}
	
	/** The party who added the record corrects it (D-156). */
	@PatchMapping("/rent-records/{id}")
	public RentRecordView updateRentRecord(@PathVariable UUID id, @Valid @RequestBody UpdateRentRecordRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return rentRecords.update(id, request, caller);
	}
	
	/** The party who added the record removes it (D-156). */
	@DeleteMapping("/rent-records/{id}")
	public ResponseEntity<Void> deleteRentRecord(@PathVariable UUID id,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		rentRecords.delete(id, caller);
		return ResponseEntity.noContent().build();
	}
	
	@PostMapping("/rent-records/{id}/confirm")
	public RentRecordView confirmRentRecord(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return rentRecords.confirm(id, caller);
	}
	
	@PostMapping("/rent-records/{id}/dispute")
	public RentRecordView disputeRentRecord(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return rentRecords.dispute(id, caller);
	}
	
	/** {@code GET /properties/{id}/rent-history}: signed-in only (D-56), months only, no tenant identity (D-26). */
	@GetMapping("/properties/{id}/rent-history")
	public RentHistory rentHistory(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return rentRecords.history(id, caller);
	}
	
}
