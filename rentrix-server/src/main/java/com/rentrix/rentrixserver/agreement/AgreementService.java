package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.agreement.dto.AgreementSummary;
import com.rentrix.rentrixserver.agreement.dto.AgreementView;
import com.rentrix.rentrixserver.agreement.dto.SignedUrlView;
import com.rentrix.rentrixserver.agreement.dto.UploadAgreementRequest;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.error.ApiProblem;
import com.rentrix.rentrixserver.common.persistence.TimestampedEntity;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.storage.DocumentStorage;
import com.rentrix.rentrixserver.storage.DocumentType;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyRepository;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.verification.ApprovalGuard;
import com.rentrix.rentrixserver.verification.VerificationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rental agreements on the user side (§7.6, D-123 – D-131): a party of an {@code ACTIVE} or {@code ENDED} tenancy
 * uploads the agreement with explicit consent and types its key fields (manual extractor, D-127); the fields are
 * masked for Aadhaar numbers, the file is sniffed (PDF, JPEG, PNG, HEIC, ≤ 10 MB), hashed and stored encrypted in
 * PRIVATE storage (D-131). The uploader checks what was stored and confirms it ({@code SUBMITTED}), which puts the
 * tenancy in the team's queue ("Agreement uploaded · awaiting team check"). Only the uploader, the other party and
 * admins see it. Every write needs a party of the tenancy (else {@code 404}, no existence leak) and a verified
 * posting method (D-128, D-144).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgreementService {

	static final String STORAGE_PREFIX = "agreements";
	static final Set<TenancyStatus> OPEN_TENANCY = EnumSet.of(TenancyStatus.ACTIVE, TenancyStatus.ENDED);

	private final RentalAgreementRepository agreements;
	private final TenancyRepository tenancies;
	private final UserRepository users;
	private final ApprovalGuard approvals;
	private final AgreementExtractor extractor;
	private final AgreementRetention retention;
	private final DocumentStorage documents;

	// ---------------------------------------------------------------- writes

	/** {@code POST /me/agreements}: a {@code DRAFT} agreement for the caller's tenancy, the file stored encrypted. */
	@Transactional
	public AgreementView upload(UploadAgreementRequest request, MultipartFile file, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Tenancy tenancy = tenancies.lockByPublicId(request.tenancyId())
			.filter(t -> sideOf(t, me) != null && OPEN_TENANCY.contains(t.getStatus()))
			.orElseThrow(() -> ApiException.notFound("Tenancy"));
		TenancyParty side = sideOf(tenancy, me);
		approvals.require(me, side == TenancyParty.LANDLORD ? Role.LANDLORD : Role.TENANT);
		requireDates(request.fields());
		byte[] bytes = readFile(file);
		DocumentType type = DocumentType.sniff(bytes).orElseThrow(() -> fileError("unsupported_file_type",
			HttpStatus.UNPROCESSABLE_CONTENT, "Upload a PDF, or a JPEG, PNG or HEIC photo."));

		Optional<RentalAgreement> live = liveOf(tenancy);
		if (VerificationState.of(tenancy.getVerificationStatus()) == VerificationState.VERIFIED) {
			throw ApiException.conflict("tenancy_verified",
				"This tenancy is already verified by the team; there is no need to upload another agreement.");
		}
		live.ifPresent(this::replace);

		AgreementExtractor.ExtractedAgreement extracted = extractor.extract(bytes, type, request.fields());
		DocumentStorage.StoredDocument stored = documents.store(STORAGE_PREFIX, bytes, type);
		deleteFileIfRolledBack(stored.key());

		Instant now = TimestampedEntity.now();
		RentalAgreement a = new RentalAgreement();
		a.setCreatedAt(now);
		a.setUploader(me);
		a.setUploaderSide(side);
		a.setTenancy(tenancy);
		a.setCityCode(tenancy.getProperty().getCity().getCode());
		a.setExtractor(extracted.extractor());
		a.setSha256(stored.sha256());
		a.setContentType(type.contentType());
		a.setSizeBytes(stored.sizeBytes());
		a.setStorageKey(stored.key());
		a.setConsentAt(now);
		a.setStatus(AgreementStatus.DRAFT);
		a.setTier(AgreementTier.UNVERIFIED);
		applyFields(a, extracted, tenancy);
		if (agreements.existsSameFileOnOtherTenancy(stored.sha256(), tenancy)) {
			a.addFlags(EnumSet.of(AgreementFlag.AGREEMENT_REUSED));
		}
		retention.apply(a);
		return view(agreements.save(a), me);
	}

	/** {@code PATCH /me/agreements/{id}}: the uploader corrects the fields before the team decides. */
	@Transactional
	public AgreementView correct(UUID id, AgreementFields fields,
		AuthenticatedUser caller) {
		User me = currentUser(caller);
		RentalAgreement a = ownLocked(id, me);
		if (a.getStatus() != AgreementStatus.DRAFT && a.getStatus() != AgreementStatus.SUBMITTED) {
			throw ApiException.conflict("invalid_transition", "This agreement was already checked by the team.");
		}
		approvals.require(me, roleOf(a.getUploaderSide()));
		requireDates(fields);
		DocumentType type = DocumentType.ofContentType(a.getContentType()).orElse(DocumentType.PDF);
		byte[] file = a.getStorageKey() == null ? new byte[0] : documents.read(a.getStorageKey()).orElse(new byte[0]);
		applyFields(a, extractor.extract(file, type, fields), a.getTenancy());
		return view(a, me);
	}

	/**
	 * {@code POST /me/agreements/{id}/confirm}: the uploader confirms the stored fields; the agreement is
	 * {@code SUBMITTED} and waits for the team's check. A tenancy the team rejected before goes back to "not verified
	 * yet", so the new agreement is checked.
	 */
	@Transactional
	public AgreementView confirm(UUID id, AuthenticatedUser caller) {
		User me = currentUser(caller);
		RentalAgreement a = ownLocked(id, me);
		if (a.getStatus() == AgreementStatus.SUBMITTED) {
			return view(a, me);
		}
		if (a.getStatus() != AgreementStatus.DRAFT) {
			throw ApiException.conflict("invalid_transition", "This agreement can no longer be confirmed.");
		}
		if (a.getStorageKey() == null) {
			throw ApiException.conflict("file_deleted", "The file of this agreement is deleted; upload it again.");
		}
		approvals.require(me, roleOf(a.getUploaderSide()));
		a.setStatus(AgreementStatus.SUBMITTED);
		a.setUploaderConfirmedAt(TimestampedEntity.now());
		Tenancy tenancy = a.getTenancy();
		if (tenancy.getVerificationStatus() == VerificationState.REJECTED) {
			tenancy.setVerificationStatus(VerificationState.UNVERIFIED);
			tenancy.setVerificationDecidedBy(null);
			tenancy.setVerificationDecidedAt(null);
			tenancy.setVerificationReason(null);
		}
		return view(a, me);
	}

	/**
	 * {@code DELETE /me/agreements/{id}}: the uploader withdraws it. Before the team's decision the row and the file
	 * go; after it only the file goes (the fields, hash and decision stay, D-131).
	 */
	@Transactional
	public void delete(UUID id, AuthenticatedUser caller) {
		User me = currentUser(caller);
		RentalAgreement a = ownLocked(id, me);
		String key = a.getStorageKey();
		if (a.getStatus() == AgreementStatus.DECIDED) {
			a.setStorageKey(null);
			a.setFileDeletedAt(TimestampedEntity.now());
		} else {
			agreements.delete(a);
		}
		deleteFileAfterCommit(key);
	}

	// ----------------------------------------------------------------- reads

	/** {@code GET /me/agreements/{id}}: the uploader or the other party of its tenancy. */
	@Transactional(readOnly = true)
	public AgreementView get(UUID id, AuthenticatedUser caller) {
		User me = currentUser(caller);
		return view(visible(id, me), me);
	}

	/** {@code GET /tenancies/{id}/agreement}: the live agreement of a tenancy, to its parties; else {@code 404}. */
	@Transactional(readOnly = true)
	public AgreementView forTenancy(UUID tenancyId, AuthenticatedUser caller) {
		User me = currentUser(caller);
		Tenancy tenancy = tenancies.findByPublicId(tenancyId).filter(t -> sideOf(t, me) != null)
			.orElseThrow(() -> ApiException.notFound("Tenancy"));
		return liveOf(tenancy).map(a -> view(a, me)).orElseThrow(() -> ApiException.notFound("Agreement"));
	}

	/** {@code GET /me/agreements/{id}/url}: a 5-minute link to the file for the uploader or the other party. */
	@Transactional(readOnly = true)
	public SignedUrlView url(UUID id, AuthenticatedUser caller) {
		User me = currentUser(caller);
		return signedUrl(visible(id, me));
	}

	/** The live agreement of each of these tenancies, for the tenancy views. */
	@Transactional(readOnly = true)
	public Map<Long, AgreementSummary> summaries(Collection<Tenancy> list, User me) {
		Map<Long, AgreementSummary> out = new HashMap<>();
		liveByTenancy(list).forEach((id, a) -> out.put(id, summary(a, me)));
		return out;
	}
	
	/** The live (not replaced) agreement of each of these tenancies, by tenancy id, in one query. */
	@Transactional(readOnly = true)
	public Map<Long, RentalAgreement> liveByTenancy(Collection<Tenancy> list) {
		if (list.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = list.stream().map(Tenancy::getId).toList();
		return agreements.findLiveByTenancyIds(ids, AgreementStatus.REPLACED).stream()
			.collect(Collectors.toMap(a -> a.getTenancy().getId(), a -> a,
				(x, y) -> x.getCreatedAt().isAfter(y.getCreatedAt()) ? x : y));
	}
	
	/**
	 * The team decided the tenancy (approvals queue, D-147, D-151): its confirmed agreement is {@code DECIDED} and its
	 * file's retention clock starts (D-131). An agreement the uploader never confirmed stays a draft. Joins the
	 * decision's transaction.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void tenancyDecided(Tenancy tenancy) {
		liveOf(tenancy).filter(a -> a.getStatus() == AgreementStatus.SUBMITTED).ifPresent(a -> {
			a.setStatus(AgreementStatus.DECIDED);
			a.setDecidedAt(tenancy.getVerificationDecidedAt() != null ? tenancy.getVerificationDecidedAt()
				: TimestampedEntity.now());
			retention.apply(a);
		});
	}
	
	// --------------------------------------------------------------- shared

	public SignedUrlView signedUrl(RentalAgreement a) {
		if (a.getStorageKey() == null) {
			throw new ApiException(HttpStatus.GONE, "file_deleted",
				"This file was deleted after its check, as promised when it was uploaded.");
		}
		DocumentType type = DocumentType.ofContentType(a.getContentType()).orElse(DocumentType.PDF);
		DocumentStorage.SignedUrl url = documents.signedUrl(a.getStorageKey(), type);
		return new SignedUrlView(url.url(), url.expiresAt());
	}

	AgreementView view(RentalAgreement a, User me) {
		Tenancy t = a.getTenancy();
		AgreementFields fields = new AgreementFields(a.getLandlordName(), a.getTenantName(), a.getAddressText(),
			a.getPincode(), a.getMonthlyRentInr(), a.getDepositInr(), a.getStartDate(), a.getEndDate(),
			a.getAnchorNumber(), a.getAnchorDate());
		boolean decided = t != null && t.getVerificationDecidedAt() != null;
		return new AgreementView(
			a.getPublicId(),
			t == null ? null : t.getPublicId(),
			a.getStatus(),
			a.getTier(),
			a.getUploaderSide(),
			me != null && a.getUploader().getId().equals(me.getId()),
			a.getAnchorKind(),
			fields,
			a.flagSet().contains(AgreementFlag.AADHAAR_MASKED_ON_INTAKE),
			a.getContentType(),
			a.getSizeBytes(),
			a.getSha256(),
			a.fileAvailable(),
			a.getCreatedAt(),
			a.getUploaderConfirmedAt(),
			a.getDecidedAt(),
			a.fileAvailable() ? a.getPurgeAfter() : null,
			a.getFileDeletedAt(),
			t == null ? VerificationState.UNVERIFIED : VerificationState.of(t.getVerificationStatus()),
			decided && t.getVerificationStatus() == VerificationState.REJECTED ? t.getVerificationReason() : null);
	}

	static AgreementSummary summary(RentalAgreement a, User me) {
		return new AgreementSummary(a.getPublicId(), a.getStatus(),
			me != null && a.getUploader().getId().equals(me.getId()), a.fileAvailable(), a.getCreatedAt());
	}

	Optional<RentalAgreement> liveOf(Tenancy tenancy) {
		return agreements.findFirstByTenancyAndStatusNotOrderByCreatedAtDesc(tenancy, AgreementStatus.REPLACED);
	}

	/** The caller's side of the tenancy, or {@code null} when they are not a party. */
	static TenancyParty sideOf(Tenancy tenancy, User user) {
		if (tenancy.getLandlord().getId().equals(user.getId())) {
			return TenancyParty.LANDLORD;
		}
		if (tenancy.getTenant() != null && tenancy.getTenant().getId().equals(user.getId())) {
			return TenancyParty.TENANT;
		}
		return null;
	}

	User currentUser(AuthenticatedUser caller) {
		return users.findByPublicId(caller.publicId())
			.filter(user -> user.getDeletedAt() == null)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue."));
	}

	void deleteFileAfterCommit(String key) {
		if (key == null) {
			return;
		}
		afterCompletion(status -> {
			if (status == TransactionSynchronization.STATUS_COMMITTED) {
				deleteQuietly(key);
			}
		});
	}

	// --------------------------------------------------------------- private

	/** Readable by the uploader and both parties of the linked tenancy; anyone else gets {@code 404}. */
	private RentalAgreement visible(UUID id, User me) {
		return agreements.findByPublicId(id)
			.filter(a -> a.getStatus() != AgreementStatus.REPLACED)
			.filter(a -> a.getUploader().getId().equals(me.getId())
				|| (a.getTenancy() != null && sideOf(a.getTenancy(), me) != null))
			.orElseThrow(() -> ApiException.notFound("Agreement"));
	}

	private RentalAgreement ownLocked(UUID id, User me) {
		return agreements.lockByPublicId(id)
			.filter(a -> a.getStatus() != AgreementStatus.REPLACED)
			.filter(a -> a.getUploader().getId().equals(me.getId()))
			.orElseThrow(() -> ApiException.notFound("Agreement"));
	}

	/** A newer upload takes the place of the live agreement; the old file is deleted once this commits. */
	private void replace(RentalAgreement old) {
		String key = old.getStorageKey();
		old.setStatus(AgreementStatus.REPLACED);
		old.setStorageKey(null);
		old.setFileDeletedAt(TimestampedEntity.now());
		old.setPurgeAfter(null);
		deleteFileAfterCommit(key);
	}

	private void applyFields(RentalAgreement a, AgreementExtractor.ExtractedAgreement extracted, Tenancy tenancy) {
		AgreementFields f = extracted.fields();
		a.setLandlordName(f.landlordName());
		a.setTenantName(f.tenantName());
		a.setAddressText(f.addressText());
		a.setPincode(f.pincode());
		a.setMonthlyRentInr(f.monthlyRentInr());
		a.setDepositInr(f.depositInr());
		a.setStartDate(f.startDate());
		a.setEndDate(f.endDate());
		a.setAnchorNumber(f.anchorNumber());
		a.setAnchorNumberKey(f.anchorNumber() == null ? null
			: f.anchorNumber().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT));
		a.setAnchorDate(f.anchorDate());
		a.setAnchorKind(f.anchorNumber() == null ? AnchorKind.NONE
			: AnchorKind.forState(tenancy.getProperty().getCity().getState()));
		a.addFlags(extracted.flags());
	}

	private static void requireDates(AgreementFields fields) {
		if (fields.endDate() != null && fields.startDate() != null && fields.endDate().isBefore(fields.startDate())) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("fields.endDate", "invalid",
				"The end date must be on or after the start date.")));
		}
	}

	private static byte[] readFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.validation(List.of(new ApiProblem.FieldError("file", "required",
				"Choose the agreement file: a PDF, or a JPEG, PNG or HEIC photo.")));
		}
		if (file.getSize() > DocumentType.MAX_BYTES) {
			throw fileError("file_too_large", HttpStatus.CONTENT_TOO_LARGE, "The file is larger than 10 MB.");
		}
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read the upload", e);
		}
	}

	private static ApiException fileError(String code, HttpStatus status, String message) {
		return new ApiException(status, code, message, List.of(new ApiProblem.FieldError("file", code, message)));
	}

	private static Role roleOf(TenancyParty side) {
		return side == TenancyParty.LANDLORD ? Role.LANDLORD : Role.TENANT;
	}

	/** A file stored for a transaction that then rolled back would have no row: delete it. */
	private void deleteFileIfRolledBack(String key) {
		afterCompletion(status -> {
			if (status != TransactionSynchronization.STATUS_COMMITTED) {
				deleteQuietly(key);
			}
		});
	}

	private static void afterCompletion(java.util.function.IntConsumer onStatus) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				onStatus.accept(status);
			}
		});
	}

	private void deleteQuietly(String key) {
		try {
			documents.delete(key);
		} catch (UncheckedIOException e) {
			// The row no longer names this file, so nothing retries it: say so, without the path or the contents.
			log.warn("Could not delete a replaced or withdrawn private document file ({})",
				e.getCause().getClass().getSimpleName());
		}
	}

}
