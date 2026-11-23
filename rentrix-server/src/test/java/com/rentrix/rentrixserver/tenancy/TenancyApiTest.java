package com.rentrix.rentrixserver.tenancy;

import com.jayway.jsonpath.JsonPath;
import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyRepository;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.property.PropertyType;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.RecordingEmailSender;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tenancy lifecycle §6 with the D-35 guards and the D-91 approval gate, invites and rent records (PLAN B5). */
@IntegrationTest
@Transactional
class TenancyApiTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
	private static final Pattern INVITE_TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	RecordingEmailSender mail;

	@Autowired
	TenancyRepository tenancies;

	@Autowired
	TenancyService service;

	@Autowired
	PropertyRepository properties;

	@Autowired
	RentRecordRepository rentRecords;

	// ------------------------------------------------------------ helpers

	private Property home(User landlord) {
		return data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED);
	}

	private static String request(Property property, String email, LocalDate start, LocalDate end, long rent) {
		return """
			{"propertyId": "%s", %s "startDate": "%s", %s "initialRentInr": %d, "depositInr": 50000}
			""".formatted(property.getPublicId(), email == null ? "" : "\"counterpartyEmail\": \"" + email + "\",",
			start, end == null ? "" : "\"endDate\": \"" + end + "\",", rent);
	}

	private ResultActions create(User caller, String body) throws Exception {
		return mvc.perform(post("/tenancies").header("Authorization", support.bearer(caller))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String createdId(User caller, String body) throws Exception {
		String json = create(caller, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private ResultActions act(User caller, String id, String action) throws Exception {
		return mvc.perform(post("/tenancies/" + id + "/" + action).header("Authorization", support.bearer(caller)));
	}

	private ResultActions act(User caller, String id, String action, String body) throws Exception {
		return mvc.perform(post("/tenancies/" + id + "/" + action).header("Authorization", support.bearer(caller))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** A tenant-started tenancy confirmed by the landlord, started a year ago at Rs 25,000. */
	private String activeTenancy(User landlord, User tenant, Property property) throws Exception {
		String id = createdId(tenant, request(property, null, TODAY.minusYears(1), null, 25_000));
		act(landlord, id, "confirm").andExpect(status().isOk());
		return id;
	}

	// --------------------------------------------------------- lifecycle

	@Test
	void tenantRequestsAndLandlordConfirms() throws Exception {
		User landlord = data.approved("Lata Landlord", Role.LANDLORD);
		User tenant = data.approved("Tara Tenant", Role.TENANT);
		Property property = home(landlord);
		LocalDate start = TODAY.minusMonths(3);

		String body = create(tenant, request(property, null, start, null, 30_000))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/tenancies/")))
			.andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
			.andExpect(jsonPath("$.initiatedBy").value("TENANT"))
			.andExpect(jsonPath("$.myRole").value("TENANT"))
			.andExpect(jsonPath("$.landlord.shortName").value("Lata L."))
			.andExpect(jsonPath("$.tenant.id").value(tenant.getPublicId().toString()))
			.andExpect(jsonPath("$.property.unitLabel").doesNotExist())
			.andExpect(jsonPath("$.actions.cancel").value(true))
			.andExpect(jsonPath("$.actions.confirm").value(false))
			.andExpect(jsonPath("$.expiresAt").exists())
			.andExpect(jsonPath("$.currentRentInr").doesNotExist())
			.andExpect(jsonPath("$.currentRentFrom").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		assertThat(mail.last(landlord.getEmail()).subject()).startsWith("Confirm your tenancy");

		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").value("LANDLORD"))
			.andExpect(jsonPath("$.actions.confirm").value(true))
			.andExpect(jsonPath("$.actions.decline").value(true))
			.andExpect(jsonPath("$.actions.cancel").value(false));

		act(tenant, id, "confirm").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_counterparty"));
		act(landlord, id, "confirm")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.evidenceLevel").value("MUTUAL"))
			.andExpect(jsonPath("$.confirmedAt").exists())
			.andExpect(jsonPath("$.currentRentInr").value(30_000))
			.andExpect(jsonPath("$.currentRentFrom").value(start.toString()))
			.andExpect(jsonPath("$.actions.end").value(true))
			.andExpect(jsonPath("$.actions.addRentRecord").value(true));
		assertThat(mail.last(tenant.getEmail()).subject()).startsWith("Tenancy confirmed");

		mvc.perform(get("/tenancies/" + id + "/rent-records").header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].status").value("CONFIRMED"))
			.andExpect(jsonPath("$[0].effectiveFrom").value(start.withDayOfMonth(1).toString()))
			.andExpect(jsonPath("$[0].monthlyRentInr").value(30_000));

		for (String action : new String[] {"confirm", "decline", "cancel"}) {
			act(landlord, id, action).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("invalid_transition"));
		}
	}

	@Test
	void aSecondConfirmIsRefusedAndAddsNoRentRecord() throws Exception {
		User landlord = data.approved("Double Landlord", Role.LANDLORD);
		User tenant = data.approved("Double Tenant", Role.TENANT);
		String id = createdId(tenant, request(home(landlord), null, TODAY.minusMonths(4), null, 18_000));
		act(landlord, id, "confirm").andExpect(status().isOk());
		act(landlord, id, "confirm").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("invalid_transition"));
		mvc.perform(get("/tenancies/" + id + "/rent-records").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].effectiveFrom").value(TODAY.minusMonths(4).withDayOfMonth(1).toString()));
	}

	@Test
	void requestWithPastEndDateConfirmsAsEnded() throws Exception {
		User landlord = data.approved("Past Landlord", Role.LANDLORD);
		User tenant = data.approved("Past Tenant", Role.TENANT);
		String id = createdId(tenant, request(home(landlord), null, TODAY.minusYears(2), TODAY.minusYears(1), 20_000));
		act(landlord, id, "confirm").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENDED"))
			.andExpect(jsonPath("$.endedAt").exists())
			.andExpect(jsonPath("$.actions.end").value(false));
		act(landlord, id, "end", "{\"endDate\": \"" + TODAY + "\"}").andExpect(status().isConflict());
	}

	@Test
	void tenanciesAreVisibleToPartiesAndAdminsOnly() throws Exception {
		User landlord = data.approved("Vis Landlord", Role.LANDLORD);
		User tenant = data.approved("Vis Tenant", Role.TENANT);
		String id = createdId(tenant, request(home(landlord), null, TODAY.minusMonths(1), null, 20_000));
		User stranger = data.approved("Nosy Neighbour", Role.TENANT, Role.LANDLORD);
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		act(stranger, id, "decline").andExpect(status().isNotFound());
		mvc.perform(get("/tenancies/" + id)).andExpect(status().isUnauthorized());
		User admin = data.user("Ada Admin", Role.ADMIN, Role.TENANT);
		mvc.perform(get("/tenancies/" + id).with(adminHost()).header("Authorization", support.adminBearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").doesNotExist());
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(admin)))
			.andExpect(status().isNotFound());
	}

	@Test
	void createValidatesDatesRolesAndListing() throws Exception {
		User landlord = data.approved("Rule Landlord", Role.LANDLORD);
		User tenant = data.approved("Rule Tenant", Role.TENANT);
		Property property = home(landlord);
		create(tenant, request(property, null, TODAY.plusDays(1), null, 20_000))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("startDate"));
		create(tenant, request(property, null, TODAY.minusMonths(2), TODAY.minusMonths(3), 20_000))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("before_start"));
		create(tenant, request(property, "x@example.test", TODAY.minusMonths(2), null, 20_000))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("counterpartyEmail"));
		create(tenant, request(property, null, TODAY.minusMonths(2), null, 0))
			.andExpect(status().isUnprocessableContent());
		Property draft = data.property(landlord, "BLR", "Koramangala", PropertyStatus.DRAFT);
		create(tenant, request(draft, null, TODAY.minusMonths(2), null, 20_000)).andExpect(status().isNotFound());
		create(landlord, request(draft, "t@example.test", TODAY.minusMonths(2), null, 20_000))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("property_not_listed"));
		User otherLandlord = data.approved("Other Landlord", Role.LANDLORD);
		create(otherLandlord, request(property, null, TODAY.minusMonths(2), null, 20_000))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("role_required"));
		create(landlord, request(property, landlord.getEmail(), TODAY.minusMonths(2), null, 20_000))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("invite_self"));
		create(landlord, request(property, null, TODAY.minusMonths(2), null, 20_000))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("required"));
	}

	// ------------------------------------------- phone gate (open by default, D-132)

	@Test
	void initiatorWithoutAVerifiedPhoneIsRefused() throws Exception {
		User landlord = data.approved("Gate Landlord", Role.LANDLORD);
		User tenant = data.verified("Waiting Tenant", Role.TENANT);
		create(tenant, request(home(landlord), null, TODAY.minusMonths(2), null, 20_000))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("id_verification_required"))
			.andExpect(jsonPath("$.missing[0]").value("method"))
			.andExpect(jsonPath("$.approval").doesNotExist());
		assertThat(tenancies.findByTenantIdAndStatusIn(tenant.getId(), java.util.List.of(TenancyStatus.values())))
			.isEmpty();
	}

	@Test
	void phoneVerifiedUserWithoutApprovalCanPostAtOnce() throws Exception {
		User landlord = data.approved("Open Landlord", Role.LANDLORD);
		User tenant = data.verified("Open Tenant", Role.TENANT);
		data.verifyPhone(tenant); // application still DRAFT: approval is a green tick, not a gate (D-132)
		create(tenant, request(home(landlord), null, TODAY.minusMonths(2), null, 20_000))
			.andExpect(status().isCreated());
	}

	@Test
	void counterpartyWithoutAVerifiedPhoneCannotConfirmButCanDecline() throws Exception {
		User landlord = data.verified("Waiting Landlord", Role.LANDLORD);
		User tenant = data.approved("Keen Tenant", Role.TENANT);
		String id = createdId(tenant, request(home(landlord), null, TODAY.minusMonths(2), null, 20_000));
		act(landlord, id, "confirm")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("id_verification_required"))
			.andExpect(jsonPath("$.missing[0]").value("method"));
		act(landlord, id, "decline", "{\"reason\": \"Never rented to this person.\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DECLINED"))
			.andExpect(jsonPath("$.declineReason").value("Never rented to this person."));
		assertThat(mail.last(tenant.getEmail()).body()).contains("Never rented to this person.");
	}

	@Test
	void unapprovedInitiatorCanCancelAndUnapprovedPartyCanEnd() throws Exception {
		User landlord = data.verified("Cancel Landlord", Role.LANDLORD);
		User tenant = data.verified("Cancel Tenant", Role.TENANT);
		Property property = home(landlord);
		Tenancy pending = data.tenancy(property, tenant, TODAY.minusMonths(5), null, TenancyStatus.PENDING_CONFIRMATION);
		String pendingId = pending.getPublicId().toString();
		act(tenant, pendingId, "cancel").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_initiator"));
		act(landlord, pendingId, "cancel").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		act(landlord, pendingId, "cancel").andExpect(status().isConflict());

		Tenancy active = data.tenancy(property, tenant, TODAY.minusMonths(4), null, TenancyStatus.ACTIVE);
		String activeId = active.getPublicId().toString();
		act(tenant, activeId, "end", "{\"endDate\": \"" + TODAY.plusDays(1) + "\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("not_in_future"));
		act(tenant, activeId, "end", "{\"endDate\": \"" + TODAY.minusMonths(6) + "\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("before_start"));
		act(tenant, activeId, "end", "{\"endDate\": \"" + TODAY + "\", \"depositRefundedInr\": 40000}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENDED"))
			.andExpect(jsonPath("$.endDate").value(TODAY.toString()))
			.andExpect(jsonPath("$.depositRefundedInr").value(40_000));
		assertThat(mail.last(landlord.getEmail()).subject()).startsWith("Tenancy ended");
		act(tenant, activeId, "end", "{\"endDate\": \"" + TODAY + "\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("invalid_transition"));
	}

	// ------------------------------------------------------------- guards

	@Test
	void overlappingTenancyOfTheSameTenantIsRefused() throws Exception {
		User landlord = data.approved("Overlap Landlord", Role.LANDLORD);
		User tenant = data.approved("Overlap Tenant", Role.TENANT);
		Property property = home(landlord);
		data.tenancy(property, tenant, TODAY.minusYears(2), TODAY.minusYears(1), TenancyStatus.ENDED);
		create(tenant, request(property, null, TODAY.minusYears(1).minusMonths(2), null, 20_000))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("tenancy_overlap"));
		// After the old tenancy ended: fine.
		createdId(tenant, request(property, null, TODAY.minusMonths(6), null, 20_000));
		// Declined or cancelled requests do not block.
		User other = data.approved("Other Tenant", Role.TENANT);
		data.tenancy(property, other, TODAY.minusMonths(3), null, TenancyStatus.DECLINED);
		createdId(other, request(property, null, TODAY.minusMonths(3), null, 20_000));
	}

	@Test
	void aWholeHomeTakesOneTenancyAtATime() throws Exception {
		User landlord = data.approved("Whole Home Landlord", Role.LANDLORD);
		User sitting = data.approved("Sitting Tenant", Role.TENANT);
		User next = data.approved("Next Tenant", Role.TENANT);
		Property property = home(landlord);
		Tenancy current = data.tenancy(property, sitting, LocalDate.of(2024, 4, 1), null, TenancyStatus.ACTIVE);

		// The landlord's invite overlaps the open-ended tenancy: refused, and the landlord learns which one.
		create(landlord, request(property, next.getEmail(), TODAY.minusMonths(1), null, 25_000))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("tenancy_overlap"))
			.andExpect(jsonPath("$.conflict.from").value("2024-04"))
			.andExpect(jsonPath("$.conflict.to").doesNotExist())
			.andExpect(jsonPath("$.conflict.status").value("ACTIVE"))
			.andExpect(jsonPath("$.conflict.tenancyId").value(current.getPublicId().toString()));
		// Another tenant's request is refused too, without the tenancy's id.
		create(next, request(property, null, TODAY.minusMonths(1), null, 25_000))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("tenancy_overlap"))
			.andExpect(jsonPath("$.detail").value(
				"This home already has a current tenancy since 2024-04. It must end before a new one can start."))
			.andExpect(jsonPath("$.conflict.from").value("2024-04"))
			.andExpect(jsonPath("$.conflict.tenancyId").doesNotExist());
		// Nothing was ended automatically.
		assertThat(tenancies.findById(current.getId()).orElseThrow().getStatus()).isEqualTo(TenancyStatus.ACTIVE);
	}

	@Test
	void aConfirmThatLostTheRaceToAnotherTenancyIsRefused() throws Exception {
		User landlord = data.approved("Racing Landlord", Role.LANDLORD);
		User first = data.approved("First Tenant", Role.TENANT);
		User second = data.approved("Second Tenant", Role.TENANT);
		Property property = home(landlord);
		// Two pending requests for the same months: both may exist while neither is confirmed.
		String a = createdId(first, request(property, null, TODAY.minusMonths(2), null, 25_000));
		String b = createdId(second, request(property, null, TODAY.minusMonths(1), null, 26_000));
		act(landlord, a, "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
		act(landlord, b, "confirm").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("tenancy_overlap"))
			.andExpect(jsonPath("$.conflict.tenancyId").value(a));
		mvc.perform(get("/tenancies/" + b).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"));
	}

	@Test
	void backToBackTenanciesOnAWholeHomeAreAllowed() throws Exception {
		User landlord = data.approved("Turnover Landlord", Role.LANDLORD);
		User before = data.approved("Earlier Tenant", Role.TENANT);
		User after = data.approved("Later Tenant", Role.TENANT);
		Property property = home(landlord);
		LocalDate lastDay = TODAY.minusMonths(3).withDayOfMonth(1).minusDays(1);
		data.tenancy(property, before, lastDay.minusYears(1), lastDay, TenancyStatus.ENDED);
		String id = createdId(landlord, request(property, after.getEmail(), lastDay.plusDays(1), null, 27_000));
		act(after, id, "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
		// The same handover day overlaps by one day.
		User late = data.approved("Overlapping Tenant", Role.TENANT);
		Property other = home(landlord);
		data.tenancy(other, before, lastDay.minusYears(1), lastDay, TenancyStatus.ENDED);
		create(late, request(other, null, lastDay, null, 27_000)).andExpect(status().isConflict());
	}

	@Test
	void aWholeHomesRentHistoryNeverShowsTwoCurrentRows() throws Exception {
		User landlord = data.approved("Legacy Landlord", Role.LANDLORD);
		User old = data.approved("Legacy Tenant", Role.TENANT);
		User recent = data.approved("Recent Tenant", Role.TENANT);
		Property property = home(landlord);
		// Two open tenancies saved before the guard existed.
		confirmedRent(data.tenancy(property, old, LocalDate.of(2025, 4, 1), null, TenancyStatus.ACTIVE), 23_500);
		confirmedRent(data.tenancy(property, recent, LocalDate.of(2026, 10, 1), null, TenancyStatus.ACTIVE), 25_000);
		mvc.perform(get("/properties/" + property.getPublicId() + "/rent-history")
				.header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.rows", hasSize(2)))
			.andExpect(jsonPath("$.rows[0].fromMonth").value("2025-04"))
			.andExpect(jsonPath("$.rows[0].toMonth").value("2026-09"))
			.andExpect(jsonPath("$.rows[1].fromMonth").value("2026-10"))
			.andExpect(jsonPath("$.rows[1].toMonth").doesNotExist());
	}

	@Test
	void backToBackTenanciesShareTheirBoundaryMonthInRentHistory() throws Exception {
		User landlord = data.approved("Boundary Landlord", Role.LANDLORD);
		User first = data.approved("Leaving Tenant", Role.TENANT);
		User second = data.approved("Arriving Tenant", Role.TENANT);
		Property property = home(landlord);
		confirmedRent(data.tenancy(property, first, LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 15),
			TenancyStatus.ENDED), 23_500);
		confirmedRent(data.tenancy(property, second, LocalDate.of(2025, 3, 16), null, TenancyStatus.ACTIVE), 25_000);
		mvc.perform(get("/properties/" + property.getPublicId() + "/rent-history")
				.header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.rows", hasSize(2)))
			.andExpect(jsonPath("$.rows[0].fromMonth").value("2024-04"))
			.andExpect(jsonPath("$.rows[0].toMonth").value("2025-03"))
			.andExpect(jsonPath("$.rows[1].fromMonth").value("2025-03"))
			.andExpect(jsonPath("$.rows[1].toMonth").doesNotExist());
	}

	private void confirmedRent(Tenancy tenancy, long rent) {
		RentRecord record = new RentRecord();
		record.setTenancy(tenancy);
		record.setEffectiveFrom(tenancy.getStartDate());
		record.setMonthlyRentInr(rent);
		record.setAddedBy(tenancy.getLandlord());
		record.setStatus(RentRecordStatus.CONFIRMED);
		rentRecords.save(record);
	}

	@Test
	void flatmatesMayOverlapInARoomOrPg() throws Exception {
		User landlord = data.approved("PG Landlord", Role.LANDLORD);
		User one = data.approved("Flat Mate One", Role.TENANT);
		User two = data.approved("Flat Mate Two", Role.TENANT);
		Property pg = home(landlord);
		pg.setPropertyType(PropertyType.PG);
		properties.save(pg);
		data.tenancy(pg, one, TODAY.minusMonths(6), null, TenancyStatus.ACTIVE);
		String id = createdId(two, request(pg, null, TODAY.minusMonths(6), null, 12_000));
		act(landlord, id, "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void atMostFivePendingRequestsPerInitiator() throws Exception {
		User landlord = data.approved("Busy Landlord", Role.LANDLORD);
		User tenant = data.approved("Busy Tenant", Role.TENANT);
		for (int i = 0; i < TenancyService.MAX_PENDING; i++) {
			createdId(tenant, request(home(landlord), null, TODAY.minusMonths(1), null, 20_000));
		}
		create(tenant, request(home(landlord), null, TODAY.minusMonths(1), null, 20_000))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("too_many_pending"));
		// Requests others sent to the tenant do not count against the tenant.
		User otherLandlord = data.approved("Inviting Landlord", Role.LANDLORD);
		createdId(otherLandlord, request(home(otherLandlord), tenant.getEmail(), TODAY.minusMonths(1), null, 20_000));
	}

	@Test
	void pendingRequestsExpireAfterFourteenDays() {
		User landlord = data.verified("Slow Landlord", Role.LANDLORD);
		User tenant = data.verified("Slow Tenant", Role.TENANT);
		Property property = home(landlord);
		Tenancy old = new Tenancy();
		old.setProperty(property);
		old.setLandlord(landlord);
		old.setTenant(tenant);
		old.setStartDate(TODAY.minusMonths(3));
		old.setInitialRentInr(20_000L);
		old.setInitiatedBy(TenancyParty.TENANT);
		old.setCreatedAt(Instant.now().minus(15, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS));
		tenancies.saveAndFlush(old);
		Tenancy fresh = data.tenancy(property, tenant, TODAY.minusMonths(1), null, TenancyStatus.PENDING_CONFIRMATION);
		User other = data.verified("Prompt Tenant", Role.TENANT);
		Tenancy confirmed = data.tenancy(property, other, TODAY.minusMonths(3), null, TenancyStatus.ACTIVE);
		confirmed.setCreatedAt(Instant.now().minus(15, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS));
		tenancies.saveAndFlush(confirmed);

		assertThat(service.expirePending()).isGreaterThanOrEqualTo(1);
		assertThat(tenancies.findById(confirmed.getId()).orElseThrow().getStatus()).isEqualTo(TenancyStatus.ACTIVE);
		assertThat(tenancies.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(TenancyStatus.EXPIRED);
		assertThat(tenancies.findById(fresh.getId()).orElseThrow().getStatus())
			.isEqualTo(TenancyStatus.PENDING_CONFIRMATION);
	}

	// ------------------------------------------------------------ invites

	@Test
	void inviteBindsOnlyToTheMatchingVerifiedEmail() throws Exception {
		User landlord = data.approved("Inviting Owner", Role.LANDLORD);
		String email = "new-tenant-" + UUID.randomUUID() + "@example.test";
		String body = create(landlord, request(home(landlord), email.toUpperCase(), TODAY.minusMonths(2), null, 22_000))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.initiatedBy").value("LANDLORD"))
			.andExpect(jsonPath("$.tenant").doesNotExist())
			.andExpect(jsonPath("$.inviteEmail").value("n***@e***.test"))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		EmailMessage invite = mail.last(email);
		Matcher matcher = INVITE_TOKEN.matcher(invite.body());
		assertThat(matcher.find()).isTrue();
		String token = matcher.group(1);
		assertThat(invite.body()).contains("http://localhost:5173/tenancy-invite?token=");

		User wrong = data.approved("Wrong Person", Role.TENANT);
		claim(wrong, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("invite_email_mismatch"));
		claim(wrong, "not-a-real-token").andExpect(status().isNotFound());

		User unverified = data.user("Unverified Invitee", Role.TENANT);
		unverified.setEmail(email);
		claim(unverified, token).andExpect(status().isForbidden());
		unverified.setEmail(UUID.randomUUID() + "@example.test");

		User invitee = data.verified("Invited Tenant", Role.TENANT);
		invitee.setEmail(email);
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(invitee)))
			.andExpect(status().isNotFound());
		claim(invitee, token).andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").value("TENANT"))
			.andExpect(jsonPath("$.inviteEmail").doesNotExist())
			.andExpect(jsonPath("$.actions.confirm").value(true));
		claim(invitee, token).andExpect(status().isNotFound());
		// The invitee has no verified phone yet: confirming waits for it, declining does not.
		act(invitee, id, "confirm").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("id_verification_required"));
		data.approve(invitee);
		act(invitee, id, "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.tenant.id").value(invitee.getPublicId().toString()))
			.andExpect(jsonPath("$.inviteEmail").value("n***@e***.test"));
	}

	@Test
	void landlordRequestByEmailNeverRevealsWhetherTheAccountExists() throws Exception {
		User landlord = data.approved("Known Owner", Role.LANDLORD);
		User tenant = data.approved("Known Tenant", Role.TENANT);
		String unknown = "nobody-" + UUID.randomUUID() + "@example.test";
		String known = create(landlord, request(home(landlord), tenant.getEmail(), TODAY.minusMonths(2), null, 22_000))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String absent = create(landlord, request(home(landlord), unknown, TODAY.minusMonths(2), null, 22_000))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		java.util.Map<String, Object> knownView = JsonPath.read(known, "$");
		java.util.Map<String, Object> absentView = JsonPath.read(absent, "$");
		assertThat(knownView.keySet()).isEqualTo(absentView.keySet());
		for (java.util.Map<String, Object> view : java.util.List.of(knownView, absentView)) {
			assertThat(view.get("tenant")).isNull();
			assertThat(view.get("status")).isEqualTo("PENDING_CONFIRMATION");
			assertThat((String) view.get("inviteEmail")).matches("[a-z0-9]\\*\\*\\*@e\\*\\*\\*\\.test");
		}
		String id = (String) knownView.get("id");
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.tenant").doesNotExist())
			.andExpect(jsonPath("$.inviteEmail").value(knownView.get("inviteEmail")));
		String mine = mvc.perform(get("/me/tenancies?role=LANDLORD").header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.items", hasSize(2))).andReturn().getResponse().getContentAsString();
		java.util.List<java.util.Map<String, Object>> items = JsonPath.read(mine, "$.items");
		assertThat(items).allSatisfy(item -> assertThat(item.get("tenant")).isNull());

		// The linked account sees the request on its own side and confirms it; only then is it named.
		assertThat(mail.last(tenant.getEmail()).subject()).startsWith("Confirm your tenancy");
		mvc.perform(get("/me/tenancies?role=TENANT").header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.items[0].id").value(id))
			.andExpect(jsonPath("$.items[0].inviteEmail").doesNotExist())
			.andExpect(jsonPath("$.items[0].actions.confirm").value(true));
		act(tenant, id, "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$.tenant.id").value(tenant.getPublicId().toString()));
		User admin = data.user("Audit Admin", Role.ADMIN, Role.TENANT);
		mvc.perform(get("/tenancies/" + JsonPath.read(absent, "$.id")).with(adminHost())
				.header("Authorization", support.adminBearer(admin)))
			.andExpect(jsonPath("$.inviteEmail").value(unknown));
	}

	@Test
	void overlappingPendingRequestsDoNotBlockEachOtherUntilOneIsConfirmed() throws Exception {
		User landlord = data.approved("Resending Landlord", Role.LANDLORD);
		User tenant = data.approved("Resent Tenant", Role.TENANT);
		Property property = home(landlord);
		String first = createdId(landlord, request(property, tenant.getEmail(), TODAY.minusMonths(3), null, 20_000));
		String second = createdId(landlord, request(property, tenant.getEmail(), TODAY.minusMonths(2), null, 21_000));
		act(tenant, second, "confirm").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
		act(tenant, first, "confirm").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("tenancy_overlap"));
		act(tenant, first, "decline").andExpect(status().isOk());
	}

	@Test
	void anAccountWithoutTheTenantRoleIsOnlyInvited() throws Exception {
		User landlord = data.approved("Inviting Landlord Two", Role.LANDLORD);
		User otherLandlord = data.approved("Landlord Only", Role.LANDLORD);
		String id = createdId(landlord,
			request(home(landlord), otherLandlord.getEmail(), TODAY.minusMonths(2), null, 22_000));
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(otherLandlord)))
			.andExpect(status().isNotFound());
		assertThat(INVITE_TOKEN.matcher(mail.last(otherLandlord.getEmail()).body()).find()).isTrue();
		assertThat(tenancies.findByPublicId(UUID.fromString(id)).orElseThrow().getTenant()).isNull();
	}

	private ResultActions claim(User caller, String token) throws Exception {
		return mvc.perform(post("/tenancy-invites/claim").header("Authorization", support.bearer(caller))
			.contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"" + token + "\"}"));
	}

	// ------------------------------------------------ rent records + history

	@Test
	void rentRecordsNeedTheOtherPartyAndOnlyConfirmedOnesAreHistory() throws Exception {
		User landlord = data.approved("Rent Landlord", Role.LANDLORD);
		User tenant = data.approved("Rent Tenant", Role.TENANT);
		Property property = home(landlord);
		String id = activeTenancy(landlord, tenant, property);
		LocalDate start = TODAY.minusYears(1).withDayOfMonth(1);
		LocalDate raise = start.plusMonths(11);

		String body = mvc.perform(post("/tenancies/" + id + "/rent-records")
				.header("Authorization", support.bearer(landlord)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"" + raise.plusDays(9) + "\", \"monthlyRentInr\": 27500}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PROPOSED"))
			.andExpect(jsonPath("$.effectiveFrom").value(raise.toString()))
			.andExpect(jsonPath("$.addedByMe").value(true))
			.andExpect(jsonPath("$.canRespond").value(false))
			.andReturn().getResponse().getContentAsString();
		String recordId = JsonPath.read(body, "$.id");
		assertThat(mail.last(tenant.getEmail()).subject()).startsWith("Confirm a rent change");

		mvc.perform(post("/tenancies/" + id + "/rent-records").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"" + raise + "\", \"monthlyRentInr\": 26000}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("rent_record_exists"));
		mvc.perform(post("/tenancies/" + id + "/rent-records").header("Authorization", support.bearer(tenant))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"" + start.minusMonths(1) + "\", \"monthlyRentInr\": 26000}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("outside_tenancy"));

		User signedIn = data.user("Curious Browser", Role.TENANT);
		mvc.perform(get("/properties/" + property.getPublicId() + "/rent-history")
				.header("Authorization", support.bearer(signedIn)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows", hasSize(1)))
			.andExpect(jsonPath("$.rows[0].toMonth").doesNotExist());

		mvc.perform(post("/rent-records/" + recordId + "/confirm").header("Authorization", support.bearer(landlord)))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_counterparty"));
		mvc.perform(post("/rent-records/" + recordId + "/confirm").header("Authorization", support.bearer(signedIn)))
			.andExpect(status().isNotFound());
		mvc.perform(post("/rent-records/" + recordId + "/confirm").header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
		mvc.perform(post("/rent-records/" + recordId + "/dispute").header("Authorization", support.bearer(tenant)))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_transition"));

		String disputed = JsonPath.read(mvc.perform(post("/tenancies/" + id + "/rent-records")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"" + start.plusMonths(5) + "\", \"monthlyRentInr\": 21000}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/rent-records/" + disputed + "/dispute").header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPUTED"));

		// A pending request elsewhere on the property never shows (saved directly: the API refuses one that
		// overlaps the current tenancy of this whole home, D-23).
		User other = data.approved("Pending Tenant", Role.TENANT);
		data.tenancy(property, other, TODAY.minusMonths(2), null, TenancyStatus.PENDING_CONFIRMATION);

		mvc.perform(get("/properties/" + property.getPublicId() + "/rent-history")
				.header("Authorization", support.bearer(signedIn)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.propertyId").value(property.getPublicId().toString()))
			.andExpect(jsonPath("$.rows", hasSize(2)))
			.andExpect(jsonPath("$.rows[0].fromMonth").value(start.toString().substring(0, 7)))
			.andExpect(jsonPath("$.rows[0].toMonth").value(raise.minusMonths(1).toString().substring(0, 7)))
			.andExpect(jsonPath("$.rows[0].monthlyRentInr").value(25_000))
			.andExpect(jsonPath("$.rows[1].fromMonth").value(raise.toString().substring(0, 7)))
			.andExpect(jsonPath("$.rows[1].toMonth").doesNotExist())
			.andExpect(jsonPath("$.rows[1].monthlyRentInr").value(27_500))
			.andExpect(jsonPath("$.rows[1].share").value(false))
			.andExpect(jsonPath("$.rows[0].tenant").doesNotExist())
			.andExpect(jsonPath("$.rows[0].startDate").doesNotExist());
		mvc.perform(get("/properties/" + property.getPublicId() + "/rent-history")).andExpect(status().isUnauthorized());

		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.currentRentInr").value(27_500))
			.andExpect(jsonPath("$.currentRentFrom").value(raise.toString()));
		mvc.perform(get("/tenancies/" + id + "/rent-records").header("Authorization", support.bearer(landlord)))
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[1].status").value("DISPUTED"))
			.andExpect(jsonPath("$[1].canRespond").value(false));

		// A confirmed rent from next month is history, but not yet the current rent.
		String upcoming = JsonPath.read(mvc.perform(post("/tenancies/" + id + "/rent-records")
				.header("Authorization", support.bearer(landlord)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"effectiveFrom\": \"" + TODAY.withDayOfMonth(1).plusMonths(1)
					+ "\", \"monthlyRentInr\": 30000}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/rent-records/" + upcoming + "/confirm").header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk());
		mvc.perform(get("/tenancies/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.currentRentInr").value(27_500))
			.andExpect(jsonPath("$.currentRentFrom").value(raise.toString()));
	}

	@Test
	void rentRecordsNeedAConfirmedTenancyAndAPartyWithAVerifiedPhone() throws Exception {
		User landlord = data.verified("Unapproved Rent Landlord", Role.LANDLORD);
		User tenant = data.verified("Unapproved Rent Tenant", Role.TENANT);
		Property property = home(landlord);
		Tenancy pending = data.tenancy(property, tenant, TODAY.minusMonths(5), null, TenancyStatus.PENDING_CONFIRMATION);
		String rent = "{\"effectiveFrom\": \"" + TODAY.withDayOfMonth(1) + "\", \"monthlyRentInr\": 26000}";
		mvc.perform(post("/tenancies/" + pending.getPublicId() + "/rent-records")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON).content(rent))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("tenancy_not_confirmed"));
		Tenancy active = data.tenancy(property, tenant, TODAY.minusMonths(4), null, TenancyStatus.ACTIVE);
		mvc.perform(post("/tenancies/" + active.getPublicId() + "/rent-records")
				.header("Authorization", support.bearer(tenant)).contentType(MediaType.APPLICATION_JSON).content(rent))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("id_verification_required"));
	}

	@Test
	void roomAndPgHistoryRowsAreShares() throws Exception {
		User landlord = data.approved("Pg Landlord", Role.LANDLORD);
		Property pg = home(landlord);
		pg.setPropertyType(com.rentrix.rentrixserver.property.PropertyType.PG);
		User a = data.approved("Room Mate A", Role.TENANT);
		User b = data.approved("Room Mate B", Role.TENANT);
		activeTenancy(landlord, a, pg);
		activeTenancy(landlord, b, pg);
		mvc.perform(get("/properties/" + pg.getPublicId() + "/rent-history").header("Authorization", support.bearer(a)))
			.andExpect(jsonPath("$.rows", hasSize(2)))
			.andExpect(jsonPath("$.rows[0].share").value(true));
	}

	// ------------------------------------------------------- /me/tenancies

	@Test
	void myTenanciesFiltersByRoleAndStatus() throws Exception {
		User both = data.approved("Both Sides", Role.LANDLORD, Role.TENANT);
		User landlord = data.approved("Their Landlord", Role.LANDLORD);
		User tenant = data.approved("Their Tenant", Role.TENANT);
		String asTenant = activeTenancy(landlord, both, home(landlord));
		String asLandlord = createdId(tenant, request(home(both), null, TODAY.minusMonths(1), null, 15_000));
		String auth = support.bearer(both);
		mvc.perform(get("/me/tenancies").header("Authorization", auth))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalItems").value(2))
			.andExpect(jsonPath("$.items[0].id").value(asLandlord));
		mvc.perform(get("/me/tenancies?role=TENANT").header("Authorization", auth))
			.andExpect(jsonPath("$.page.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(asTenant));
		mvc.perform(get("/me/tenancies?role=LANDLORD&status=PENDING_CONFIRMATION").header("Authorization", auth))
			.andExpect(jsonPath("$.page.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].property.unitLabel").doesNotExist())
			.andExpect(jsonPath("$.items[0].actions.confirm").value(true));
		mvc.perform(get("/me/tenancies?status=ACTIVE,ENDED&sort=start,asc").header("Authorization", auth))
			.andExpect(jsonPath("$.page.totalItems").value(1));
		mvc.perform(get("/me/tenancies?sort=bogus").header("Authorization", auth))
			.andExpect(status().isUnprocessableContent());
		mvc.perform(get("/me/tenancies")).andExpect(status().isUnauthorized());
	}

}
