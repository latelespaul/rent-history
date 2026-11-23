package com.rentrix.rentrixserver.tenancy;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.edit.EditHistory;
import com.rentrix.rentrixserver.edit.EditTarget;
import com.rentrix.rentrixserver.property.FieldVisibility;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.dto.CreateRentRecordRequest;
import com.rentrix.rentrixserver.tenancy.dto.RentHistory;
import com.rentrix.rentrixserver.tenancy.dto.RentRecordView;
import com.rentrix.rentrixserver.tenancy.dto.UpdateRentRecordRequest;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.verification.ApprovalGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Rent records (§5.5, D-25) and the public rent history (D-26). Either party adds a rent change to a confirmed
 * tenancy; it is {@code PROPOSED} until the other party confirms or disputes it. Adding, confirming and disputing
 * need an approved account for the acting side (D-91); an admin may confirm or dispute without it. The party who added
 * a record may change or delete it (D-156); that takes the tenancy's green tick away until the team checks it again.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentRecordService {

	private static final EnumSet<RentRecordStatus> LIVE = EnumSet.of(RentRecordStatus.PROPOSED, RentRecordStatus.CONFIRMED);

	private final TenancyService tenancyService;
	private final RentRecordRepository rentRecords;
	private final PropertyRepository properties;
	private final ApprovalGuard approvals;
	private final TenancyNotifier notifier;
	private final EditHistory history;

	/** {@code GET /tenancies/{id}/rent-records}: every record, any state, for parties and admins. */
	public List<RentRecordView> list(UUID tenancyId, AuthenticatedUser caller) {
		User me = tenancyService.currentUser(caller);
		Tenancy tenancy = tenancyService.readable(tenancyId, me, caller);
		return rentRecords.findByTenancyIdOrderByEffectiveFromAsc(tenancy.getId()).stream()
			.map(record -> view(record, me))
			.toList();
	}

	/**
	 * {@code POST /tenancies/{id}/rent-records}: a rent change from a month on, within the tenancy (from its start
	 * month to its end date, or to next month while it is current). One live record per month.
	 */
	@Transactional
	public RentRecordView add(UUID tenancyId, CreateRentRecordRequest request, AuthenticatedUser caller) {
		User me = tenancyService.currentUser(caller);
		Tenancy tenancy = tenancyService.readable(tenancyId, me, caller);
		TenancyParty side = TenancyService.sideOf(tenancy, me);
		if (side == null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_party", "Only the landlord or tenant can add rent.");
		}
		if (!TenancyService.CONFIRMED.contains(tenancy.getStatus())) {
			throw ApiException.conflict("tenancy_not_confirmed",
				"Rent can be added once the tenancy is current or ended.");
		}
		approvals.require(me, TenancyService.roleOf(side));
		LocalDate month = request.effectiveFrom().withDayOfMonth(1);
		requireMonth(tenancy, month);
		if (rentRecords.existsByTenancyIdAndEffectiveFromAndStatusIn(tenancy.getId(), month, LIVE)) {
			throw ApiException.conflict("rent_record_exists", "There is already a rent record for " + YearMonth.from(month)
				+ ".");
		}
		RentRecord record = new RentRecord();
		record.setTenancy(tenancy);
		record.setEffectiveFrom(month);
		record.setMonthlyRentInr(request.monthlyRentInr());
		record.setMaintenanceInr(request.maintenanceInr());
		record.setAddedBy(me);
		record.setStatus(RentRecordStatus.PROPOSED);
		RentRecord saved = rentRecords.saveAndFlush(record);
		notifier.rentRecordToConfirm(saved, side == TenancyParty.LANDLORD ? tenancy.getTenant() : tenancy.getLandlord());
		return view(saved, me);
	}

	/**
	 * {@code PATCH /rent-records/{id}}: the party who added a rent change corrects its month or amounts (D-156), with
	 * the same month rules as on add. A record the other side had confirmed waits for them again ({@code PROPOSED}).
	 */
	@Transactional
	public RentRecordView update(UUID recordId, UpdateRentRecordRequest request, AuthenticatedUser caller) {
		User me = tenancyService.currentUser(caller);
		RentRecord record = own(recordId, me, caller);
		Tenancy tenancy = record.getTenancy();
		approvals.require(me, TenancyService.roleOf(TenancyService.sideOf(tenancy, me)));
		LocalDate month = request.effectiveFrom().withDayOfMonth(1);
		requireMonth(tenancy, month);
		boolean taken = rentRecords.findByTenancyIdOrderByEffectiveFromAsc(tenancy.getId()).stream()
			.anyMatch(r -> r != record && LIVE.contains(r.getStatus()) && r.getEffectiveFrom().equals(month));
		if (taken) {
			throw ApiException.conflict("rent_record_exists", "There is already a rent record for " + YearMonth.from(month)
				+ ".");
		}
		String label = "rent from " + YearMonth.from(record.getEffectiveFrom());
		EditHistory.Changes changes = new EditHistory.Changes()
			.field(label + ": month", YearMonth.from(record.getEffectiveFrom()), YearMonth.from(month))
			.field(label + ": monthlyRentInr", record.getMonthlyRentInr(), request.monthlyRentInr())
			.field(label + ": maintenanceInr", record.getMaintenanceInr(), request.maintenanceInr());
		if (changes.isEmpty()) {
			return view(record, me);
		}
		record.setEffectiveFrom(month);
		record.setMonthlyRentInr(request.monthlyRentInr());
		record.setMaintenanceInr(request.maintenanceInr());
		if (record.getStatus() == RentRecordStatus.CONFIRMED) {
			record.setStatus(RentRecordStatus.PROPOSED);
		}
		boolean wasVerified = TenancyService.unverify(tenancy);
		history.edited(me, EditTarget.TENANCY, tenancy.getId(), tenancy.getPublicId(), wasVerified, changes);
		return view(rentRecords.saveAndFlush(record), me);
	}

	/** {@code DELETE /rent-records/{id}}: the party who added a rent change removes it (D-156). */
	@Transactional
	public void delete(UUID recordId, AuthenticatedUser caller) {
		User me = tenancyService.currentUser(caller);
		RentRecord record = own(recordId, me, caller);
		Tenancy tenancy = record.getTenancy();
		boolean wasVerified = TenancyService.unverify(tenancy);
		history.deleted(me, EditTarget.TENANCY, tenancy.getId(), tenancy.getPublicId(), wasVerified,
			"rent from " + YearMonth.from(record.getEffectiveFrom()), "rent " + record.getMonthlyRentInr()
				+ (record.getMaintenanceInr() == null ? "" : ", maintenance " + record.getMaintenanceInr()));
		rentRecords.delete(record);
	}

	/**
	 * A rent change the caller added, on a current or ended tenancy, and not the starting rent: that one is the
	 * tenancy's own rent and is changed on the tenancy ({@code PATCH /tenancies/{id}}).
	 */
	private RentRecord own(UUID recordId, User me, AuthenticatedUser caller) {
		RentRecord record = rentRecords.findByPublicId(recordId)
			.filter(r -> TenancyService.sideOf(r.getTenancy(), me) != null || caller.hasRole(Role.ADMIN))
			.orElseThrow(() -> ApiException.notFound("Rent record"));
		if (!record.getAddedBy().getId().equals(me.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_creator",
				"Only the person who added this rent record can change or delete it.");
		}
		if (!TenancyService.CONFIRMED.contains(record.getTenancy().getStatus())) {
			throw ApiException.conflict("tenancy_not_confirmed", "This tenancy is " + record.getTenancy().getStatus() + ".");
		}
		if (starting(record)) {
			throw ApiException.rule("starting_rent",
				"This is the rent you moved in with. Change it on the tenancy instead.");
		}
		return record;
	}

	/** The tenancy's first rent record, made from its starting rent. */
	private static boolean starting(RentRecord record) {
		Tenancy tenancy = record.getTenancy();
		return record.getEffectiveFrom().equals(tenancy.getStartDate().withDayOfMonth(1))
			&& Objects.equals(tenancy.getInitialRentInr(), record.getMonthlyRentInr());
	}

	private void requireMonth(Tenancy tenancy, LocalDate month) {
		LocalDate first = tenancy.getStartDate().withDayOfMonth(1);
		LocalDate last = tenancy.getEndDate() != null ? tenancy.getEndDate()
			: tenancyService.today().withDayOfMonth(1).plusMonths(1);
		if (month.isBefore(first) || month.isAfter(last)) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("effectiveFrom", "outside_tenancy",
				"Pick a month from " + YearMonth.from(first) + " to " + YearMonth.from(last) + ".")));
		}
	}

	/** {@code POST /rent-records/{id}/confirm}: the other party (or an admin) agrees; it joins the rent history. */
	@Transactional
	public RentRecordView confirm(UUID recordId, AuthenticatedUser caller) {
		return respond(recordId, caller, RentRecordStatus.CONFIRMED);
	}

	/** {@code POST /rent-records/{id}/dispute}: the other party (or an admin) says it is wrong; it stays out. */
	@Transactional
	public RentRecordView dispute(UUID recordId, AuthenticatedUser caller) {
		return respond(recordId, caller, RentRecordStatus.DISPUTED);
	}

	/**
	 * {@code GET /properties/{id}/rent-history} (signed-in, D-56): confirmed records of confirmed tenancies as month
	 * ranges, without tenant identity or exact dates (D-26, D-57). A record runs until the month before the next
	 * confirmed record of its tenancy, or to the tenancy's end month; the latest of a current tenancy has no end.
	 * On a whole home the rows of mutual tenancies never overlap and at most the newest is open-ended (D-23); a
	 * tenant's one-sided rows (D-142) are listed as they are and never shorten them.
	 */
	public RentHistory history(UUID propertyId, AuthenticatedUser caller) {
		Property property = properties.findByPublicIdAndDeletedAtIsNull(propertyId)
			.filter(p -> p.getStatus().isPublic() || p.getLandlord().getPublicId().equals(caller.publicId())
				|| caller.hasRole(Role.ADMIN))
			.orElseThrow(() -> ApiException.notFound("Property"));
		boolean share = property.getPropertyType() == PropertyType.ROOM || property.getPropertyType() == PropertyType.PG;
		Map<Long, List<RentRecord>> byTenancy = new LinkedHashMap<>();
		// A one-sided tenancy (D-139, D-142) has nobody who confirmed its rent, so its tenant's own records show,
		// marked not confirmed; proposals on a tenancy both sides confirmed stay hidden until confirmed (D-25).
		rentRecords.findByTenancyPropertyIdAndStatusInAndTenancyStatusInOrderByEffectiveFromAsc(property.getId(),
				List.of(RentRecordStatus.CONFIRMED, RentRecordStatus.PROPOSED), TenancyService.CONFIRMED)
			.stream()
			.filter(r -> r.getStatus() == RentRecordStatus.CONFIRMED || r.getTenancy().getConfirmedAt() == null)
			.forEach(r -> byTenancy.computeIfAbsent(r.getTenancy().getId(), id -> new ArrayList<>()).add(r));
		List<Row> rows = new ArrayList<>();
		for (List<RentRecord> records : byTenancy.values()) {
			Tenancy tenancy = records.getFirst().getTenancy();
			for (int i = 0; i < records.size(); i++) {
				RentRecord record = records.get(i);
				YearMonth from = YearMonth.from(record.getEffectiveFrom());
				YearMonth to;
				if (i + 1 < records.size()) {
					to = YearMonth.from(records.get(i + 1).getEffectiveFrom()).minusMonths(1);
				} else {
					to = tenancy.getEndDate() == null ? null : YearMonth.from(tenancy.getEndDate());
				}
				rows.add(new Row(from, to, record));
			}
		}
		Comparator<Row> order = Comparator.comparing(Row::from)
			.thenComparing(Row::to, Comparator.nullsLast(Comparator.naturalOrder()));
		rows.sort(order);
		if (!share) {
			List<Row> held = new ArrayList<>(rows.stream().filter(r -> TenancyService.mutual(r.record().getTenancy()))
				.toList());
			closeOverlaps(held);
			rows.removeIf(r -> TenancyService.mutual(r.record().getTenancy()));
			rows.addAll(held);
			rows.sort(order);
		}
		return new RentHistory(property.getPublicId(), rows.stream()
			.map(row -> {
				// D-140: a MASKED rent shows its months but not the amount.
				boolean open = row.record().getTenancy().getRentVisibility() == FieldVisibility.OPEN;
				return new RentHistory.Row(row.from().toString(), row.to() == null ? null : row.to().toString(),
					open ? row.record().getMonthlyRentInr() : null, open ? row.record().getMaintenanceInr() : null, share,
					row.record().getStatus() == RentRecordStatus.CONFIRMED);
			})
			.toList());
	}

	private record Row(YearMonth from, YearMonth to, RentRecord record) {
	}
	
	/**
	 * A whole home is let to one tenancy at a time, so its rows never overlap: a row still open (or running past)
	 * when the next one starts ends the month before it. Only data from before the overlap guard can need this.
	 */
	private static void closeOverlaps(List<Row> rows) {
		for (int i = 0; i + 1 < rows.size(); i++) {
			Row row = rows.get(i);
			YearMonth next = rows.get(i + 1).from();
			if (row.to() == null || row.to().isAfter(next)) {
				YearMonth to = next.minusMonths(1);
				rows.set(i, new Row(row.from(), to.isBefore(row.from()) ? row.from() : to, row.record()));
			}
		}
	}

	private RentRecordView respond(UUID recordId, AuthenticatedUser caller, RentRecordStatus outcome) {
		User me = tenancyService.currentUser(caller);
		RentRecord record = rentRecords.findByPublicId(recordId)
			.filter(r -> TenancyService.sideOf(r.getTenancy(), me) != null || caller.hasRole(Role.ADMIN))
			.orElseThrow(() -> ApiException.notFound("Rent record"));
		Tenancy tenancy = record.getTenancy();
		TenancyParty side = TenancyService.sideOf(tenancy, me);
		if (side != null && record.getAddedBy().getId().equals(me.getId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "not_counterparty",
				"The other party confirms or disputes a rent record you added.");
		}
		if (record.getStatus() != RentRecordStatus.PROPOSED) {
			throw ApiException.conflict("invalid_transition",
				"A " + record.getStatus() + " rent record cannot be " + outcome.name().toLowerCase(Locale.ROOT) + ".");
		}
		if (!TenancyService.CONFIRMED.contains(tenancy.getStatus())) {
			throw ApiException.conflict("tenancy_not_confirmed", "This tenancy is " + tenancy.getStatus() + ".");
		}
		if (side != null) {
			approvals.require(me, TenancyService.roleOf(side));
		}
		record.setStatus(outcome);
		return view(rentRecords.saveAndFlush(record), me);
	}

	private static RentRecordView view(RentRecord record, User me) {
		Tenancy tenancy = record.getTenancy();
		TenancyParty side = TenancyService.sideOf(tenancy, me);
		boolean addedByMe = record.getAddedBy().getId().equals(me.getId());
		boolean canRespond = side != null && !addedByMe && record.getStatus() == RentRecordStatus.PROPOSED
			&& TenancyService.CONFIRMED.contains(tenancy.getStatus());
		boolean canEdit = addedByMe && TenancyService.CONFIRMED.contains(tenancy.getStatus()) && !starting(record);
		return new RentRecordView(record.getPublicId(), record.getEffectiveFrom(), record.getMonthlyRentInr(),
			record.getMaintenanceInr(), record.getStatus(), TenancyService.party(record.getAddedBy()), addedByMe,
			canRespond, canEdit, record.getCreatedAt());
	}

}
