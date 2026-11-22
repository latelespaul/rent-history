package com.rentrix.rentrixserver.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The v1 check for PAN, masked Aadhaar and LinkedIn (D-102, D-103, D-93): the row waits as {@code PENDING} in the
 * admin identity queue ({@code ix_identity_verifications_queue}) until an admin verifies or rejects it (admin
 * endpoints come with PLAN B12). The reference is the row's {@code public_id}.
 */
@Component
@RequiredArgsConstructor
public class AdminReviewProvider implements IdentityCheckProvider {
	
	private final IdentityVerificationRepository checks;
	
	@Override
	public Set<IdentityMethod> methods() {
		return Set.of(IdentityMethod.PAN, IdentityMethod.AADHAAR, IdentityMethod.LINKEDIN);
	}
	
	@Override
	public String start(IdentityVerification check) {
		if (!methods().contains(check.getMethod())) {
			throw new IllegalArgumentException("Admin review does not check " + check.getMethod());
		}
		check.resetDecision();
		check.setStatus(IdentityStatus.PENDING);
		check.setProvider(null);
		check.setProviderRef(null);
		return checks.save(check).getPublicId().toString();
	}
	
	@Override
	public Optional<IdentityStatus> result(String ref) {
		UUID id;
		try {
			id = UUID.fromString(ref);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		return checks.findByPublicId(id).map(IdentityVerification::getStatus);
	}
	
}
