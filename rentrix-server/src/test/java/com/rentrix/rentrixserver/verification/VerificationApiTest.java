package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.admin.ModerationAction;
import com.rentrix.rentrixserver.admin.ModerationActionRepository;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.admin.ModerationTargetType;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.identity.CheckedBy;
import com.rentrix.rentrixserver.identity.IdentityMethod;
import com.rentrix.rentrixserver.identity.IdentityService;
import com.rentrix.rentrixserver.identity.IdentityStatus;
import com.rentrix.rentrixserver.identity.IdentityVerification;
import com.rentrix.rentrixserver.identity.IdentityVerificationRepository;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.RecordingEmailSender;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verification core (PLAN B11): the application state machine (D-90), submit rules (D-105), the approval gate
 * (D-91, D-105), admin decisions with audit rows (D-87) and the conflict check (D-88), and decision emails (D-65).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class VerificationApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper json;

	@Autowired
	TestData data;

	@Autowired
	AccessTokenService tokens;

	@Autowired
	UserRepository users;

	@Autowired
	IdentityService identity;

	@Autowired
	IdentityVerificationRepository checks;

	@Autowired
	VerificationApplicationRepository applications;

	@Autowired
	VerificationEventRepository events;

	@Autowired
	ModerationActionRepository audit;

	@Autowired
	PlatformTransactionManager transactionManager;
	
	@Autowired
	RecordingEmailSender mail;
	
	@Autowired
	VerificationService verification;
	
	private List<String> subjectsTo(User user) {
		return mail.to(user.getEmail()).stream().map(EmailMessage::subject).toList();
	}

	private String bearer(User user) {
		return "Bearer " + tokens.issue(user.getPublicId(), user.getRoles());
	}

	/** A user with a finished profile (display name + home city) and the given roles. */
	private User applicant(String name, Role... roles) {
		User user = data.user(name, roles);
		user.setHomeCity(data.city("BLR"));
		user.setEmailVerifiedAt(Instant.parse("2026-11-01T10:00:00Z"));
		return users.save(user);
	}

	private User admin() {
		return data.user("Admin " + UUID.randomUUID(), Role.ADMIN);
	}

	private IdentityVerification check(User user, IdentityMethod method, IdentityStatus status) {
		IdentityVerification row = new IdentityVerification();
		row.setUser(user);
		row.setMethod(method);
		row.setStatus(status);
		row.setLast4(method == IdentityMethod.PAN ? "234F" : "3210");
		if (status == IdentityStatus.VERIFIED) {
			row.setCheckedBy(method == IdentityMethod.PHONE_OTP ? CheckedBy.OTP : CheckedBy.ADMIN);
			row.setVerifiedAt(Instant.parse("2026-11-01T10:00:00Z"));
		}
		return checks.save(row);
	}

	private ResultActions submit(User user, String role) throws Exception {
		return mvc.perform(post("/me/verification/" + role + "/submit").header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private UUID submitted(User user, Role role) throws Exception {
		String body = submit(user, role.name()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(json.readTree(body).get("id").asString());
	}

	private ResultActions decide(User admin, UUID application, String decision, String body) throws Exception {
		return mvc.perform(post("/admin/applications/" + application + "/" + decision)
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions gatedWrite(User user, Role role) throws Exception {
		return mvc.perform(post("/test-only/gated/" + role).header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private List<ModerationAction> auditOf(UUID target) {
		return audit.findByTargetTypeAndTargetRefOrderByCreatedAtAscIdAsc(ModerationTargetType.APPLICATION,
			target.toString());
	}

	private static final String REJECT = """
		{"code": "ID_UNREADABLE", "note": "The PAN photo is blurred. Upload a sharper photo."}""";

	@Test
	void meVerificationListsEveryHeldRoleAsDraftWithItsSteps() throws Exception {
		User user = applicant("Both roles", Role.TENANT, Role.LANDLORD);
		mvc.perform(get("/me/verification").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].role").value("TENANT"))
			.andExpect(jsonPath("$[0].status").value("DRAFT"))
			.andExpect(jsonPath("$[0].revision").value(1))
			.andExpect(jsonPath("$[0].id").doesNotExist())
			.andExpect(jsonPath("$[0].steps.profile").value("DONE"))
			.andExpect(jsonPath("$[0].steps.identity").value("MISSING"))
			.andExpect(jsonPath("$[0].steps.linkedin").value("SKIPPED"))
			.andExpect(jsonPath("$[0].steps.rentalAgreement").value("SKIPPED"))
			.andExpect(jsonPath("$[1].role").value("LANDLORD"))
			.andExpect(jsonPath("$[1].steps.rentalAgreement").value("NOT_APPLICABLE"));
		mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(jsonPath("$.verification.TENANT").value("DRAFT"))
			.andExpect(jsonPath("$.verification.LANDLORD").value("DRAFT"));
	}

	@Test
	void submitWithoutAnyIdMethodIsRefusedWithTheMissingSteps() throws Exception {
		User user = applicant("No ID", Role.TENANT);
		submit(user, "TENANT")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("verification_incomplete"))
			.andExpect(jsonPath("$.missing.length()").value(1))
			.andExpect(jsonPath("$.missing[0]").value("identity"));

		User noProfile = data.user("No profile", Role.LANDLORD);
		check(noProfile, IdentityMethod.PAN, IdentityStatus.PENDING);
		submit(noProfile, "landlord")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.missing[0]").value("profile"))
			.andExpect(jsonPath("$.missing.length()").value(1));
		assertThat(applications.findByUser(user)).isEmpty();
	}

	@Test
	void submitForARoleNotHeldIsNotFound() throws Exception {
		User user = applicant("Tenant only", Role.TENANT);
		check(user, IdentityMethod.PHONE_OTP, IdentityStatus.VERIFIED);
		submit(user, "LANDLORD").andExpect(status().isNotFound());
		submit(user, "ADMIN").andExpect(status().isNotFound());
		submit(user, "nonsense").andExpect(status().isNotFound());
	}

	@Test
	void stateMachineWithIllegalTransitionsAndResubmitAfterReject() throws Exception {
		User user = applicant("Lifecycle", Role.TENANT);
		check(user, IdentityMethod.PAN, IdentityStatus.PENDING);
		User admin = admin();

		UUID id = submitted(user, Role.TENANT);
		mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(jsonPath("$.verification.TENANT").value("PENDING"));
		submit(user, "TENANT").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("invalid_transition"));
		decide(admin, id, "revoke", REJECT).andExpect(status().isConflict());

		decide(admin, id, "reject", REJECT)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.decisionCode").value("ID_UNREADABLE"));
		decide(admin, id, "reject", REJECT).andExpect(status().isConflict());
		decide(admin, id, "approve", "{}").andExpect(status().isConflict());
		mvc.perform(get("/me/verification").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(jsonPath("$[0].status").value("REJECTED"))
			.andExpect(jsonPath("$[0].decision.code").value("ID_UNREADABLE"))
			.andExpect(jsonPath("$[0].decision.note").value("The PAN photo is blurred. Upload a sharper photo."));
		assertThat(mail.last(user.getEmail()).subject()).isEqualTo("Your Doorway Living tenant verification needs changes");
		assertThat(mail.last(user.getEmail()).body()).contains("The PAN photo is blurred. Upload a sharper photo.");

		submit(user, "tenant")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.revision").value(2))
			.andExpect(jsonPath("$.decision").doesNotExist());

		check(user, IdentityMethod.PHONE_OTP, IdentityStatus.VERIFIED);
		decide(admin, id, "approve", "{\"note\": \"Looks fine.\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.revision").value(2));
		submit(user, "TENANT").andExpect(status().isConflict());
		assertThat(mail.last(user.getEmail()).subject()).isEqualTo("Your Doorway Living tenant account is approved");
		mvc.perform(get("/me/verification").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(jsonPath("$[0].decision.note").doesNotExist());

		decide(admin, id, "revoke", "{\"code\": \"DUPLICATE_ACCOUNT\", \"note\": \"Same person as another account.\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"));
		assertThat(subjectsTo(user)).containsExactly("Your Doorway Living tenant verification needs changes",
			"Your Doorway Living tenant account is approved", "Your Doorway Living tenant approval was withdrawn");

		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			VerificationApplication application = applications.findByPublicId(id).orElseThrow();
			List<String> history = new ArrayList<>();
			for (VerificationEvent event : events.findByApplicationOrderByCreatedAtAscIdAsc(application)) {
				history.add(event.getEvent() + "/" + event.getRevision());
			}
			assertThat(history).containsExactly("SUBMITTED/1", "REJECTED/1", "SUBMITTED/2", "APPROVED/2", "REVOKED/2");
		});
	}

	@Test
	void everyDecisionWritesOneAuditRowWithItsReason() throws Exception {
		User user = applicant("Audited", Role.LANDLORD);
		check(user, IdentityMethod.AADHAAR, IdentityStatus.VERIFIED);
		User admin = admin();
		UUID id = submitted(user, Role.LANDLORD);
		decide(admin, id, "reject", REJECT).andExpect(status().isOk());
		submitted(user, Role.LANDLORD);
		decide(admin, id, "approve", "{\"note\": \"Checked the masked Aadhaar.\"}").andExpect(status().isOk());
		decide(admin, id, "revoke", "{\"code\": \"OTHER\", \"note\": \"New facts came up.\"}").andExpect(status().isOk());

		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			List<ModerationAction> rows = auditOf(id);
			assertThat(rows).extracting(ModerationAction::getAction).containsExactly(
				ModerationActionType.REJECT_APPLICATION, ModerationActionType.APPROVE_APPLICATION,
				ModerationActionType.REVOKE_APPROVAL);
			assertThat(rows).extracting(ModerationAction::getReason).containsExactly(
				"ID_UNREADABLE: The PAN photo is blurred. Upload a sharper photo.", "Checked the masked Aadhaar.",
				"OTHER: New facts came up.");
			assertThat(rows).allMatch(r -> r.getAdmin().getId().equals(admin.getId()) && r.getCreatedAt() != null);
		});
	}

	@Test
	void rejectAndRevokeNeedACodeAndANote() throws Exception {
		User user = applicant("Reasons", Role.TENANT);
		check(user, IdentityMethod.PAN, IdentityStatus.PENDING);
		UUID id = submitted(user, Role.TENANT);
		User admin = admin();
		decide(admin, id, "reject", "{\"code\": \"OTHER\"}").andExpect(status().isUnprocessableContent());
		decide(admin, id, "reject", "{\"note\": \"No code\"}").andExpect(status().isUnprocessableContent());
		decide(admin, id, "reject", "{\"code\": \"LINKEDIN_MISMATCH\", \"note\": \"x\"}")
			.andExpect(status().isBadRequest());
		assertThat(auditOf(id)).isEmpty();
	}

	@Test
	void approveWithOnlyPendingIdMethodsIsRefusedAndKeepsNothing() throws Exception {
		User user = applicant("Only pending", Role.TENANT);
		IdentityVerification pan = check(user, IdentityMethod.PAN, IdentityStatus.PENDING);
		check(user, IdentityMethod.AADHAAR, IdentityStatus.PENDING);
		UUID id = submitted(user, Role.TENANT);
		User admin = admin();
		decide(admin, id, "approve", "{}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("no_verified_id"));
		// Rejecting the only pending PAN inside the call still leaves no verified ID: nothing is kept.
		decide(admin, id, "approve", "{\"identity\": [{\"id\": \"" + pan.getPublicId()
			+ "\", \"result\": \"REJECTED\", \"code\": \"ID_UNREADABLE\"}]}")
			.andExpect(status().isUnprocessableContent());
		assertThat(checks.findByPublicId(pan.getPublicId()).orElseThrow().getStatus()).isEqualTo(IdentityStatus.PENDING);
		assertThat(applications.findByPublicId(id).orElseThrow().getStatus()).isEqualTo(VerificationStatus.PENDING);
		assertThat(auditOf(id)).isEmpty();
	}

	@Test
	void approveMayDecideAPendingPanInTheSameCall() throws Exception {
		User user = applicant("PAN in call", Role.LANDLORD);
		IdentityVerification pan = check(user, IdentityMethod.PAN, IdentityStatus.PENDING);
		UUID id = submitted(user, Role.LANDLORD);
		User admin = admin();
		decide(admin, id, "approve", "{\"identity\": [{\"id\": \"" + pan.getPublicId() + "\", \"result\": \"VERIFIED\"}]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"));
		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			IdentityVerification decided = checks.findByPublicId(pan.getPublicId()).orElseThrow();
			assertThat(decided.getStatus()).isEqualTo(IdentityStatus.VERIFIED);
			assertThat(decided.getCheckedBy()).isEqualTo(CheckedBy.ADMIN);
			assertThat(decided.getVerifiedBy().getId()).isEqualTo(admin.getId());
			assertThat(audit.findByTargetTypeAndTargetRefOrderByCreatedAtAscIdAsc(ModerationTargetType.IDENTITY,
				pan.getPublicId().toString())).extracting(ModerationAction::getAction)
				.containsExactly(ModerationActionType.VERIFY_IDENTITY);
			assertThat(auditOf(id)).extracting(ModerationAction::getAction)
				.containsExactly(ModerationActionType.APPROVE_APPLICATION);
			VerificationApplication application = applications.findByPublicId(id).orElseThrow();
			assertThat(events.findByApplicationOrderByCreatedAtAscIdAsc(application))
				.extracting(VerificationEvent::getEvent)
				.containsExactly(VerificationEventType.SUBMITTED, VerificationEventType.IDENTITY_CHECKED,
					VerificationEventType.APPROVED);
		});
	}

	@Test
	void approveCannotDecideAnotherUsersOrANonPendingCheck() throws Exception {
		User user = applicant("Own checks only", Role.TENANT);
		check(user, IdentityMethod.PHONE_OTP, IdentityStatus.VERIFIED);
		IdentityVerification phone = checks.findByUserAndMethod(user, IdentityMethod.PHONE_OTP).orElseThrow();
		User stranger = applicant("Stranger", Role.TENANT);
		IdentityVerification strangerPan = check(stranger, IdentityMethod.PAN, IdentityStatus.PENDING);
		UUID id = submitted(user, Role.TENANT);
		User admin = admin();
		decide(admin, id, "approve", "{\"identity\": [{\"id\": \"" + strangerPan.getPublicId()
			+ "\", \"result\": \"VERIFIED\"}]}").andExpect(status().isNotFound());
		decide(admin, id, "approve", "{\"identity\": [{\"id\": \"" + phone.getPublicId()
			+ "\", \"result\": \"VERIFIED\"}]}").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("identity_not_decidable"));
		assertThat(checks.findByPublicId(strangerPan.getPublicId()).orElseThrow().getStatus())
			.isEqualTo(IdentityStatus.PENDING);
	}

	@Test
	void guardDeniesDraftPendingAndRejectedAndAllowsApprovedAndIdVerified() throws Exception {
		User user = applicant("Gated", Role.TENANT);
		gatedWrite(user, Role.TENANT)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("approval_required"))
			.andExpect(jsonPath("$.approval.role").value("TENANT"))
			.andExpect(jsonPath("$.approval.status").value("DRAFT"));
		check(user, IdentityMethod.PHONE_OTP, IdentityStatus.VERIFIED);
		UUID id = submitted(user, Role.TENANT);
		gatedWrite(user, Role.TENANT).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.approval.status").value("PENDING"));
		User admin = admin();
		decide(admin, id, "reject", REJECT).andExpect(status().isOk());
		gatedWrite(user, Role.TENANT).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.approval.status").value("REJECTED"));
		submitted(user, Role.TENANT);

		String tokenBeforeApproval = bearer(user);
		decide(admin, id, "approve", "{}").andExpect(status().isOk());
		// The same token: the gate reads the decision from the database, not from the JWT (D-91).
		mvc.perform(post("/test-only/gated/TENANT").header(HttpHeaders.AUTHORIZATION, tokenBeforeApproval))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.written").value("TENANT"));
		// Approval is per role: the landlord side is still gated.
		gatedWrite(user, Role.LANDLORD).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.approval.role").value("LANDLORD"));

		decide(admin, id, "revoke", REJECT).andExpect(status().isOk());
		mvc.perform(post("/test-only/gated/TENANT").header(HttpHeaders.AUTHORIZATION, tokenBeforeApproval))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.approval.status").value("REJECTED"));
	}

	@Test
	void guardDeniesAnApprovedUserOnceTheOnlyIdMethodIsRevoked() throws Exception {
		User user = applicant("Phone changed", Role.LANDLORD);
		check(user, IdentityMethod.PHONE_OTP, IdentityStatus.VERIFIED);
		UUID id = submitted(user, Role.LANDLORD);
		decide(admin(), id, "approve", "{}").andExpect(status().isOk());
		gatedWrite(user, Role.LANDLORD).andExpect(status().isOk());

		new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
			identity.phoneChanged(users.findById(user.getId()).orElseThrow()));
		gatedWrite(user, Role.LANDLORD)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("id_verification_required"));
		assertThat(applications.findByPublicId(id).orElseThrow().getStatus()).isEqualTo(VerificationStatus.APPROVED);

		// Verifying another method lifts it without a new approval (D-105).
		check(user, IdentityMethod.AADHAAR, IdentityStatus.VERIFIED);
		gatedWrite(user, Role.LANDLORD).andExpect(status().isOk());
	}

	@Test
	void aWriteBasedOnAStaleReadLosesWithInvalidTransition() throws Exception {
		User user = applicant("Raced", Role.TENANT);
		check(user, IdentityMethod.PHONE_OTP, IdentityStatus.VERIFIED);
		UUID id = submitted(user, Role.TENANT);
		VerificationApplication stale = applications.findByPublicId(id).orElseThrow();
		decide(admin(), id, "approve", "{}").andExpect(status().isOk());

		stale.setStatus(VerificationStatus.REJECTED);
		assertThatThrownBy(() -> verification.save(stale))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(e.getCode()).isEqualTo("invalid_transition");
			});
		assertThat(applications.findByPublicId(id).orElseThrow().getStatus()).isEqualTo(VerificationStatus.APPROVED);
		assertThat(auditOf(id)).extracting(ModerationAction::getAction)
			.containsExactly(ModerationActionType.APPROVE_APPLICATION);
	}

	@Test
	void adminCannotDecideTheirOwnApplication() throws Exception {
		User admin = applicant("Admin and tenant", Role.TENANT, Role.ADMIN);
		check(admin, IdentityMethod.PHONE_OTP, IdentityStatus.VERIFIED);
		UUID id = submitted(admin, Role.TENANT);
		decide(admin, id, "approve", "{}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("admin_conflict"));
		decide(admin, id, "reject", REJECT).andExpect(status().isForbidden());
		assertThat(auditOf(id)).isEmpty();
		decide(admin(), id, "approve", "{}").andExpect(status().isOk());
	}

	@Test
	void nonAdminsAreRefused() throws Exception {
		User user = applicant("Wants approval", Role.TENANT);
		check(user, IdentityMethod.PHONE_OTP, IdentityStatus.VERIFIED);
		UUID id = submitted(user, Role.TENANT);
		User other = applicant("Not an admin", Role.LANDLORD);
		decide(other, id, "approve", "{}").andExpect(status().isForbidden());
		decide(user, id, "approve", "{}").andExpect(status().isForbidden());

		// A token that still says ADMIN after the role was removed in the database is refused too (D-19).
		User former = admin();
		String staleToken = bearer(former);
		former.getRoles().clear();
		users.save(former);
		mvc.perform(post("/admin/applications/" + id + "/approve").header(HttpHeaders.AUTHORIZATION, staleToken)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
		assertThat(applications.findByPublicId(id).orElseThrow().getStatus()).isEqualTo(VerificationStatus.PENDING);
		decide(admin(), UUID.randomUUID(), "approve", "{}").andExpect(status().isNotFound());
	}

	@Test
	void threeSubmitsPer24Hours() throws Exception {
		User user = applicant("Resubmits a lot", Role.TENANT);
		check(user, IdentityMethod.PAN, IdentityStatus.PENDING);
		User admin = admin();
		UUID id = submitted(user, Role.TENANT);
		for (int i = 0; i < 2; i++) {
			decide(admin, id, "reject", REJECT).andExpect(status().isOk());
			submitted(user, Role.TENANT);
		}
		decide(admin, id, "reject", REJECT).andExpect(status().isOk());
		submit(user, "TENANT")
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		assertThat(applications.findByPublicId(id).orElseThrow().getRevision()).isEqualTo(3);
	}

	@Test
	void decisionEmailsNeverCarryIdNumbers(CapturedOutput output) throws Exception {
		User user = applicant("Emailed", Role.TENANT);
		IdentityVerification pan = check(user, IdentityMethod.PAN, IdentityStatus.PENDING);
		UUID id = submitted(user, Role.TENANT);
		decide(admin(), id, "approve", "{\"identity\": [{\"id\": \"" + pan.getPublicId() + "\", \"result\": \"VERIFIED\"}]}")
			.andExpect(status().isOk());
		EmailMessage approved = mail.last(user.getEmail());
		assertThat(approved.subject()).isEqualTo("Your Doorway Living tenant account is approved");
		assertThat(approved.subject() + approved.body()).doesNotContain("234F").doesNotContain("XXXXXX");
		assertThat(output).doesNotContain(user.getEmail()).doesNotContain("234F");
		JsonNode me = json.readTree(mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andReturn().getResponse().getContentAsString());
		assertThat(me.get("verification").get("TENANT").asString()).isEqualTo("APPROVED");
		assertThat(me.get("badges").get("idVerified").asBoolean()).isTrue();
	}

}
