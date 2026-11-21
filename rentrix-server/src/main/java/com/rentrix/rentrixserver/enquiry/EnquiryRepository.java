package com.rentrix.rentrixserver.enquiry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
	
	Optional<Enquiry> findByPublicId(UUID publicId);
	
	/** D-32: open enquiries of a tenant. */
	long countByTenantIdAndStatus(Long tenantId, EnquiryStatus status);
	
	/** D-32: enquiries a tenant sent since a point in time. */
	long countByTenantIdAndCreatedAtAfter(Long tenantId, Instant since);
	
	/** D-54: does this landlord hold a live enquiry grant for this tenant? */
	boolean existsByTenantIdAndPropertyLandlordIdAndStatusAndExpiresAtAfter(Long tenantId, Long landlordId,
		EnquiryStatus status, Instant now);
	
}
