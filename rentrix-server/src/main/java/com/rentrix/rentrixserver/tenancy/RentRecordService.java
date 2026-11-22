package com.rentrix.rentrixserver.tenancy;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.dto.CreateRentRecordRequest;
import com.rentrix.rentrixserver.tenancy.dto.RentHistory;
import com.rentrix.rentrixserver.tenancy.dto.RentRecordView;
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
import java.util.UUID;

/**
 * Rent records (§5.5, D-25) and the public rent history (D-26). Either party adds a rent change to a confirmed
 * tenancy; it is {@code PROPOSED} until the other party confirms or disputes it. Adding, confirming and disputing
 * need an approved account for the acting side (D-91); an admin may confirm or dispute without it.
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
				"Rent can be added once both parties confirmed the tenancy.");
		}
		approvals.require(me, TenancyService.roleOf(side));
		LocalDate month = request.effectiveFrom().withDayOfMonth(1);
		LocalDate first = tenancy.getStartDate().withDayOfMonth(1);
		LocalDate last = tenancy.getEndDate() != null ? tenancy.getEndDate()
			: tenancyService.today().withDayOfMonth(1).plusMonths(1);
		if (month.isBefore(first) || month.isAfter(last)) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("effectiveFrom", "outside_tenancy",
				"Pick a month from " + YearMonth.from(first) + " to " + YearMonth.from(last) + ".")));
		}
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
	 */
	public RentHistory history(UUID propertyId, AuthenticatedUser caller) {
		Property property = properties.findByPublicIdAndDeletedAtIsNull(propertyId)
			.filter(p -> p.getStatus().isPublic() || p.getLandlord().getPublicId().equals(caller.publicId())
				|| caller.hasRole(Role.ADMIN))
			.orElseThrow(() -> ApiException.notFound("Property"));
		boolean share = property.getPropertyType() == PropertyType.ROOM || property.getPropertyType() == PropertyType.PG;
		Map<Long, List<RentRecord>> byTenancy = new LinkedHashMap<>();
		rentRecords.findByTenancyPropertyIdAndStatusAndTenancyStatusInOrderByEffectiveFromAsc(property.getId(),
				RentRecordStatus.CONFIRMED, TenancyService.CONFIRMED)
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
		rows.sort(Comparator.comparing(Row::from).thenComparing(Row::to, Comparator.nullsLast(Comparator.naturalOrder())));
		return new RentHistory(property.getPublicId(), rows.stream()
			.map(row -> new RentHistory.Row(row.from().toString(), row.to() == null ? null : row.to().toString(),
				row.record().getMonthlyRentInr(), row.record().getMaintenanceInr(), share))
			.toList());
	}

	private record Row(YearMonth from, YearMonth to, RentRecord record) {
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
		return new RentRecordView(record.getPublicId(), record.getEffectiveFrom(), record.getMonthlyRentInr(),
			record.getMaintenanceInr(), record.getStatus(), TenancyService.party(record.getAddedBy()), addedByMe,
			canRespond, record.getCreatedAt());
	}

}
