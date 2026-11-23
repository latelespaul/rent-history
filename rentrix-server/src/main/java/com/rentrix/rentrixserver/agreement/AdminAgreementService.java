package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.admin.AuditLog;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.admin.ModerationTargetType;
import com.rentrix.rentrixserver.agreement.dto.AdminAgreementView;
import com.rentrix.rentrixserver.agreement.dto.SignedUrlView;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.common.web.PageResponse;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.verification.VerificationState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The team's side of rental agreements (D-130, D-136, D-151): the list of agreements waiting for a check, each with its
 * tenancy, flags and a link to the file (every view audited {@code VIEW_DOCUMENT}, D-95). The decision itself is the
 * approvals queue's tenancy approve / reject ({@code /admin/tenancies/{id}/approve|reject}, audited
 * {@code VERIFY_ITEM} / {@code REJECT_ITEM}), which also decides the agreement ({@link AgreementService#tenancyDecided}).
 * {@code /admin/**} is admin-host only.
 */
@Service
@RequiredArgsConstructor
public class AdminAgreementService {
	
	private final RentalAgreementRepository agreements;
	private final AgreementService service;
	private final AuditLog audit;
	
	/** {@code GET /admin/agreements?status=}: oldest first; {@code SUBMITTED} (waiting for the team) by default. */
	@Transactional(readOnly = true)
	public PageResponse<AdminAgreementView> list(Set<AgreementStatus> statuses, Pageable pageable) {
		Set<AgreementStatus> filter = statuses == null || statuses.isEmpty()
			? EnumSet.of(AgreementStatus.SUBMITTED) : statuses;
		return PageResponse.of(agreements.findByStatusIn(filter, pageable), this::view);
	}
	
	@Transactional(readOnly = true)
	public AdminAgreementView get(UUID id) {
		return view(find(id));
	}
	
	/** {@code GET /admin/agreements/{id}/url}: a 5-minute link to the file, audited {@code VIEW_DOCUMENT}. */
	@Transactional
	public SignedUrlView url(UUID id, AuthenticatedUser caller) {
		RentalAgreement a = find(id);
		SignedUrlView url = service.signedUrl(a);
		audit.record(service.currentUser(caller), ModerationActionType.VIEW_DOCUMENT, ModerationTargetType.AGREEMENT,
			a.getId(), a.getPublicId().toString(), null);
		return url;
	}
	
	private RentalAgreement find(UUID id) {
		return agreements.findByPublicId(id).orElseThrow(() -> ApiException.notFound("Agreement"));
	}
	
	private AdminAgreementView view(RentalAgreement a) {
		Tenancy t = a.getTenancy();
		AdminAgreementView.TenancyFacts facts = null;
		if (t != null) {
			Property p = t.getProperty();
			String home = p.getBuildingName() != null && !p.getBuildingName().isBlank() ? p.getBuildingName()
				: p.getLocality();
			facts = new AdminAgreementView.TenancyFacts(t.getPublicId(), p.getPublicId(), home, p.getCity().getName(),
				p.getUnitLabel(), t.getStartDate(), t.getEndDate(), t.getInitialRentInr(), t.getDepositInr(),
				t.getStatus(), !UnclaimedLandlord.is(t.getLandlord()), VerificationState.of(t.getVerificationStatus()));
		}
		return new AdminAgreementView(service.view(a, null), a.flagSet(),
			new AdminAgreementView.Person(a.getUploader().getPublicId(), a.getUploader().getDisplayName()), facts);
	}
	
}
