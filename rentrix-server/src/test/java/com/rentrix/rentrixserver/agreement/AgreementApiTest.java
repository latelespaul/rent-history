package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.admin.ModerationAction;
import com.rentrix.rentrixserver.admin.ModerationActionRepository;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.verification.VerificationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rental agreement intake (D-123 – D-131): a party uploads the agreement with consent and types its fields; Aadhaar
 * numbers are masked in every field; the file is stored encrypted and is readable only through a 5-minute signed link
 * that only the two parties and admins get; the team verifies or rejects the tenancy from it (audited); retention
 * deletes the file and keeps the fields. Not transactional, so after-commit file deletes really run.
 */
@IntegrationTest
class AgreementApiTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
	private static final String FAKE_AADHAAR = "2345 6789 0123";
	/** A tiny PDF with a fake Aadhaar number in its text, the way a scanned Maharashtra agreement can show one. */
	private static final byte[] PDF = ("%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n"
		+ "BT (Leave and Licence. Licensee Aadhaar " + FAKE_AADHAAR + ") Tj ET\n%%EOF\n")
		.getBytes(StandardCharsets.US_ASCII);

	@Autowired
	MockMvc mvc;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	ObjectMapper json;

	@Autowired
	RentalAgreementRepository agreements;

	@Autowired
	TenancyRepository tenancies;

	@Autowired
	ModerationActionRepository audit;

	@Autowired
	AgreementRetentionJob retentionJob;

	@Value("${app.storage.local-dir}")
	String storageDir;

	private User landlord;
	private User tenant;
	private Tenancy tenancy;

	@BeforeEach
	void tenancy() {
		landlord = data.verified("Agreement Landlord", Role.LANDLORD);
		data.verifyPhone(landlord);
		tenant = data.verified("Agreement Tenant", Role.TENANT);
		data.verifyPhone(tenant);
		Property home = data.property(landlord, "PNQ", "Baner", PropertyStatus.PUBLISHED);
		tenancy = data.tenancy(home, tenant, TODAY.minusMonths(6), null, TenancyStatus.ACTIVE);
	}

	private String data(UUID tenancyId, boolean consent, String tenantName) {
		return """
			{"tenancyId": "%s", "consent": %s, "fields": {"landlordName": "Shri Ravi Patil",
			 "tenantName": "%s", "addressText": "Flat 12, Baner Road, Pune. Aadhaar 9876-5432-1098",
			 "pincode": "411045", "monthlyRentInr": 25000, "depositInr": 100000, "startDate": "%s",
			 "endDate": "%s", "anchorNumber": "4567/2025"}}""".formatted(tenancyId, consent, tenantName,
			TODAY.minusMonths(6), TODAY.plusMonths(5));
	}

	private ResultActions upload(User user, byte[] file, String body) throws Exception {
		return mvc.perform(multipart("/me/agreements")
			.file(new MockMultipartFile("file", "agreement.pdf", "application/pdf", file))
			.file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, body.getBytes(StandardCharsets.UTF_8)))
			.header("Authorization", support.bearer(user)));
	}

	private JsonNode uploaded(User user) throws Exception {
		return read(upload(user, PDF, data(tenancy.getPublicId(), true, "Asha " + FAKE_AADHAAR + " Kulkarni"))
			.andExpect(status().isCreated()));
	}

	private JsonNode read(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private JsonNode confirmed(User user) throws Exception {
		String id = uploaded(user).get("id").asString();
		return read(mvc.perform(post("/me/agreements/" + id + "/confirm").header("Authorization", support.bearer(user)))
			.andExpect(status().isOk()));
	}

	private ResultActions download(String url) throws Exception {
		return mvc.perform(get(url.substring(url.indexOf("/documents/"))));
	}

	@Test
	void theUploaderSeesMaskedFieldsAndConfirmsThemForTheTeamsCheck() throws Exception {
		JsonNode draft = uploaded(tenant);

		assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
		assertThat(draft.get("uploaderSide").asString()).isEqualTo("TENANT");
		assertThat(draft.get("uploadedByMe").asBoolean()).isTrue();
		assertThat(draft.get("aadhaarMasked").asBoolean()).isTrue();
		assertThat(draft.at("/fields/tenantName").asString()).isEqualTo("Asha XXXX XXXX 0123 Kulkarni");
		assertThat(draft.at("/fields/addressText").asString()).endsWith("Aadhaar XXXX XXXX 1098");
		assertThat(draft.get("anchorKind").asString()).isEqualTo("MH_LEAVE_LICENCE");
		assertThat(draft.get("contentType").asString()).isEqualTo("application/pdf");
		assertThat(draft.get("sha256").asString()).isEqualTo(com.rentrix.rentrixserver.storage.DocumentStorage.sha256(PDF));
		assertThat(draft.toString()).doesNotContain("23456789").doesNotContain("6789 0123").doesNotContain("9876");

		String id = draft.get("id").asString();
		mvc.perform(post("/me/agreements/" + id + "/confirm").header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.tenancyVerification").value("UNVERIFIED"));

		// The tenancy says "Agreement uploaded · awaiting team check" to both parties.
		mvc.perform(get("/tenancies/" + tenancy.getPublicId()).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agreement.id").value(id))
			.andExpect(jsonPath("$.agreement.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.agreement.uploadedByMe").value(true))
			.andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
		mvc.perform(get("/me/tenancies").header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].agreement.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.items[0].agreement.uploadedByMe").value(false));
	}

	@Test
	void aFakeAadhaarNeverReachesTheDatabaseOrTheDiskInPlainText() throws Exception {
		String id = uploaded(tenant).get("id").asString();
		RentalAgreement row = agreements.findByPublicId(UUID.fromString(id)).orElseThrow();

		assertThat(row.getTenantName()).isEqualTo("Asha XXXX XXXX 0123 Kulkarni");
		assertThat(row.flagSet()).contains(AgreementFlag.AADHAAR_MASKED_ON_INTAKE);
		assertThat(row.getStorageKey()).startsWith("agreements/").doesNotContain("agreement.pdf");
		Path stored = Path.of(storageDir).toAbsolutePath().normalize().resolve("private").resolve(row.getStorageKey());
		byte[] onDisk = Files.readAllBytes(stored);
		assertThat(onDisk).isNotEqualTo(PDF);
		assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).doesNotContain(FAKE_AADHAAR).doesNotContain("%PDF");
		assertThat(Arrays.equals(onDisk, 0, 4, "RXD1".getBytes(StandardCharsets.US_ASCII), 0, 4)).isTrue();
		// Nothing under the storage folder holds the number in plain text.
		try (Stream<Path> files = Files.walk(Path.of(storageDir))) {
			assertThat(files.filter(Files::isRegularFile).map(AgreementApiTest::latin1))
				.noneMatch(text -> text.contains(FAKE_AADHAAR));
		}
	}

	@Test
	void onlyTheTwoPartiesAndAdminsGetTheFileThroughAShortLivedSignedLink() throws Exception {
		String id = confirmed(tenant).get("id").asString();

		JsonNode link = read(mvc.perform(get("/me/agreements/" + id + "/url").header("Authorization", support.bearer(tenant)))
			.andExpect(status().isOk()));
		Instant expires = Instant.parse(link.get("expiresAt").asString());
		assertThat(Duration.between(Instant.now(), expires)).isLessThanOrEqualTo(Duration.ofMinutes(5));
		String url = link.get("url").asString();
		download(url)
			.andExpect(status().isOk())
			.andExpect(content().contentType("application/pdf"))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(content().bytes(PDF));
		// A changed token is refused without saying why.
		download(url.substring(0, url.length() - 2) + "AA").andExpect(status().isNotFound());

		// The other party of the tenancy may open it too.
		mvc.perform(get("/me/agreements/" + id + "/url").header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk());
		mvc.perform(get("/tenancies/" + tenancy.getPublicId() + "/agreement").header("Authorization", support.bearer(landlord)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.uploadedByMe").value(false));

		// Anyone else: 404, no existence leak.
		User stranger = data.verified("Agreement Stranger", Role.TENANT);
		data.verifyPhone(stranger);
		mvc.perform(get("/me/agreements/" + id + "/url").header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/me/agreements/" + id).header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/tenancies/" + tenancy.getPublicId() + "/agreement").header("Authorization", support.bearer(stranger)))
			.andExpect(status().isNotFound());
		upload(stranger, PDF, data(tenancy.getPublicId(), true, "Asha Kulkarni")).andExpect(status().isNotFound());

		// Admins: on the admin host only, and every view is audited.
		User admin = data.user("Agreement Admin", Role.ADMIN);
		mvc.perform(get("/admin/agreements/" + id + "/url").header("Authorization", support.adminBearer(admin)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/admin/agreements/" + id + "/url").with(adminHost()).header("Authorization", support.adminBearer(admin)))
			.andExpect(status().isOk());
		assertThat(audit.findAll()).anyMatch(row -> row.getAction() == ModerationActionType.VIEW_DOCUMENT
			&& id.equals(row.getTargetRef()) && row.getAdmin().getId().equals(admin.getId()));
	}

	@Test
	void uploadsNeedConsentARealFileTheKeyFieldsAndAVerifiedMethod() throws Exception {
		String tenancyId = tenancy.getPublicId().toString();
		upload(tenant, PDF, data(tenancy.getPublicId(), false, "Asha"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("consent"))
			.andExpect(jsonPath("$.errors[0].message").value("Tick the box to agree before you upload."));
		upload(tenant, "just some text, not a document".getBytes(StandardCharsets.UTF_8),
			data(tenancy.getPublicId(), true, "Asha"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("unsupported_file_type"))
			.andExpect(jsonPath("$.errors[0].field").value("file"));
		byte[] tooBig = new byte[(int) com.rentrix.rentrixserver.storage.DocumentType.MAX_BYTES + 1];
		System.arraycopy(PDF, 0, tooBig, 0, PDF.length);
		upload(tenant, tooBig, data(tenancy.getPublicId(), true, "Asha"))
			.andExpect(status().isContentTooLarge())
			.andExpect(jsonPath("$.code").value("file_too_large"));
		String noRent = """
			{"tenancyId": "%s", "consent": true, "fields": {"landlordName": "Ravi", "tenantName": "Asha",
			 "startDate": "%s", "endDate": "%s"}}""".formatted(tenancyId, TODAY.minusMonths(1), TODAY.minusMonths(2));
		upload(tenant, PDF, noRent)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("fields.monthlyRentInr"))
			.andExpect(jsonPath("$.errors[0].message").value("Enter the monthly rent from the agreement."));

		User unverified = data.verified("Agreement No Method", Role.TENANT);
		Tenancy theirs = data.tenancy(data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED),
			unverified, TODAY.minusMonths(3), null, TenancyStatus.ACTIVE);
		upload(unverified, PDF, data(theirs.getPublicId(), true, "Asha"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("id_verification_required"));
		assertThat(agreements.findAll()).noneMatch(a -> a.getTenancy() != null
			&& a.getTenancy().getId().equals(theirs.getId()));
	}

	private ResultActions decide(User admin, String path, String body) throws Exception {
		return mvc.perform(post("/admin/" + path).with(adminHost()).header("Authorization", support.adminBearer(admin))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}
	
	@Test
	void theTeamSeesTheAgreementOnTheTenancyCardAndItsTickDecidesTheAgreement() throws Exception {
		String id = confirmed(tenant).get("id").asString();
		User admin = data.user("Agreement Admin", Role.ADMIN);
		
		// The agreements list, oldest first, with the tenancy, the uploader and the admin-only flags.
		JsonNode list = read(mvc.perform(get("/admin/agreements").param("size", "50").with(adminHost())
			.header("Authorization", support.adminBearer(admin))).andExpect(status().isOk()));
		assertThat(list.get("items").toString()).contains(id);
		mvc.perform(get("/admin/agreements/" + id).with(adminHost()).header("Authorization", support.adminBearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tenancy.id").value(tenancy.getPublicId().toString()))
			.andExpect(jsonPath("$.uploader.displayName").value("Agreement Tenant"))
			.andExpect(jsonPath("$.flags[0]").value("AADHAAR_MASKED_ON_INTAKE"))
			.andExpect(jsonPath("$.agreement.fields.tenantName").value("Asha XXXX XXXX 0123 Kulkarni"));
		
		// The approvals queue's tenancy card carries the agreement: what was typed, its flags and the file to open.
		JsonNode queue = read(mvc.perform(get("/admin/queue").param("type", "TENANCY").with(adminHost())
			.header("Authorization", support.adminBearer(admin))).andExpect(status().isOk()));
		JsonNode card = null;
		for (JsonNode item : queue.get("items")) {
			if (item.get("id").asString().equals(tenancy.getPublicId().toString())) {
				card = item;
			}
		}
		assertThat(card).as("the tenancy waits in the approvals queue").isNotNull();
		assertThat(card.get("agreementId").asString()).isEqualTo(id);
		assertThat(card.get("details").toString())
			.contains("Rental agreement uploaded by the tenant")
			.contains("tenant Asha XXXX XXXX 0123 Kulkarni")
			.contains("stamp / registration no. 4567/2025")
			.doesNotContain("2345 6789");
		assertThat(card.get("flags").toString()).contains("AADHAAR_MASKED_ON_INTAKE");
		
		decide(admin, "tenancies/" + tenancy.getPublicId() + "/approve", "{\"note\": \"Names, rent and dates match.\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("VERIFIED"));
		
		Tenancy ticked = tenancies.findByPublicId(tenancy.getPublicId()).orElseThrow();
		assertThat(ticked.getVerificationStatus()).isEqualTo(VerificationState.VERIFIED);
		assertThat(ticked.getVerificationDecidedBy().getId()).isEqualTo(admin.getId());
		assertThat(audit.findAll()).anyMatch(a -> a.getAction() == ModerationActionType.VERIFY_ITEM
			&& tenancy.getPublicId().toString().equals(a.getTargetRef()));
		RentalAgreement decided = agreements.findByPublicId(UUID.fromString(id)).orElseThrow();
		assertThat(decided.getStatus()).isEqualTo(AgreementStatus.DECIDED);
		assertThat(decided.getDecidedAt()).isNotNull();
		// The other party never confirmed this file: it goes 90 days after upload (D-131).
		assertThat(decided.getPurgeAfter()).isEqualTo(decided.getCreatedAt().plus(Duration.ofDays(90)));
		
		mvc.perform(get("/tenancies/" + tenancy.getPublicId()).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
			.andExpect(jsonPath("$.agreement.status").value("DECIDED"));
		// No new upload on a verified tenancy.
		upload(tenant, PDF, data(tenancy.getPublicId(), true, "Asha")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("tenancy_verified"));
		// Users cannot reach the admin endpoints: a main-site session is no admin session (§13.3).
		mvc.perform(get("/admin/agreements/" + id).with(adminHost()).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/admin/agreements/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isNotFound());
	}
	
	@Test
	void aRejectedTenancyShowsTheReasonAndANewAgreementReplacesTheOldOne() throws Exception {
		String first = confirmed(tenant).get("id").asString();
		User admin = data.user("Agreement Admin", Role.ADMIN);
		decide(admin, "tenancies/" + tenancy.getPublicId() + "/reject",
			"{\"code\": \"CANNOT_CONFIRM\", \"note\": \"The pages are unreadable.\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("REJECTED"));
		mvc.perform(get("/tenancies/" + tenancy.getPublicId()).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.verificationStatus").value("REJECTED"))
			.andExpect(jsonPath("$.verificationReason").value("CANNOT_CONFIRM: The pages are unreadable."));
		RentalAgreement rejected = agreements.findByPublicId(UUID.fromString(first)).orElseThrow();
		assertThat(rejected.getStatus()).isEqualTo(AgreementStatus.DECIDED);
		String oldKey = rejected.getStorageKey();
		
		String second = confirmed(tenant).get("id").asString();
		
		RentalAgreement replaced = agreements.findByPublicId(UUID.fromString(first)).orElseThrow();
		assertThat(replaced.getStatus()).isEqualTo(AgreementStatus.REPLACED);
		assertThat(replaced.getStorageKey()).isNull();
		assertThat(Files.exists(Path.of(storageDir).toAbsolutePath().normalize().resolve("private").resolve(oldKey)))
			.isFalse();
		// The new agreement puts the tenancy back in the queue, not verified yet.
		mvc.perform(get("/tenancies/" + tenancy.getPublicId()).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.agreement.id").value(second))
			.andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
			.andExpect(jsonPath("$.verificationReason").value(org.hamcrest.Matchers.nullValue()));
	}
	
	@Test
	void retentionDeletesTheFileAndKeepsTheFields() throws Exception {
		String id = confirmed(tenant).get("id").asString();
		RentalAgreement a = agreements.findByPublicId(UUID.fromString(id)).orElseThrow();
		String key = a.getStorageKey();
		a.setPurgeAfter(Instant.now().minusSeconds(60));
		agreements.saveAndFlush(a);

		assertThat(retentionJob.purge()).isGreaterThanOrEqualTo(1);

		RentalAgreement kept = agreements.findByPublicId(UUID.fromString(id)).orElseThrow();
		assertThat(kept.getStorageKey()).isNull();
		assertThat(kept.getFileDeletedAt()).isNotNull();
		assertThat(kept.getSha256()).isEqualTo(com.rentrix.rentrixserver.storage.DocumentStorage.sha256(PDF));
		assertThat(kept.getAnchorNumber()).isEqualTo("4567/2025");
		assertThat(Files.exists(Path.of(storageDir).toAbsolutePath().normalize().resolve("private").resolve(key)))
			.isFalse();
		mvc.perform(get("/me/agreements/" + id + "/url").header("Authorization", support.bearer(tenant)))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("file_deleted"));
		mvc.perform(get("/me/agreements/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(jsonPath("$.fileAvailable").value(false))
			.andExpect(jsonPath("$.fields.landlordName").value("Shri Ravi Patil"));
	}

	@Test
	void theUploaderCanWithdrawAnUndecidedAgreement() throws Exception {
		String id = uploaded(tenant).get("id").asString();
		String key = agreements.findByPublicId(UUID.fromString(id)).orElseThrow().getStorageKey();
		mvc.perform(delete("/me/agreements/" + id).header("Authorization", support.bearer(landlord)))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/me/agreements/" + id).header("Authorization", support.bearer(tenant)))
			.andExpect(status().isNoContent());
		assertThat(agreements.findByPublicId(UUID.fromString(id))).isEmpty();
		assertThat(Files.exists(Path.of(storageDir).toAbsolutePath().normalize().resolve("private").resolve(key)))
			.isFalse();
	}

	private static String latin1(Path file) {
		try {
			return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

}
