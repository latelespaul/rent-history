package com.rentrix.rentrixserver.user.profile;

import com.rentrix.rentrixserver.enquiry.EnquiryStatus;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

/** The lookups behind tenant profile visibility (D-54) and the landlord profile's counts (D-55). */
interface ProfileAccessRepository extends Repository<Tenancy, Long> {
	
	/**
	 * The landlord has a tenancy with the tenant: a confirmed one, or a request the tenant sent (an application).
	 * A landlord's own unconfirmed request does not count, as it would show whether an email has an account.
	 */
	@Query("select count(t) > 0 from Tenancy t where t.landlord.id = :landlordId and t.tenant.id = :tenantId "
		+ "and (t.status in :confirmed or (t.status = :pending and t.initiatedBy = :tenantSide))")
	boolean landlordOfTenant(@Param("landlordId") Long landlordId, @Param("tenantId") Long tenantId,
		@Param("confirmed") Collection<TenancyStatus> confirmed, @Param("pending") TenancyStatus pending,
		@Param("tenantSide") TenancyParty tenantSide);
	
	/** The tenant sent an enquiry on one of the landlord's homes that is still open (D-31). */
	@Query("select count(e) > 0 from Enquiry e where e.tenant.id = :tenantId and e.property.landlord.id = :landlordId "
		+ "and e.status = :open and e.expiresAt > :now")
	boolean openEnquiry(@Param("landlordId") Long landlordId, @Param("tenantId") Long tenantId,
		@Param("open") EnquiryStatus open, @Param("now") Instant now);
	
	@Query("select count(p) from Property p where p.landlord.id = :landlordId and p.status = :status "
		+ "and p.deletedAt is null")
	long countListed(@Param("landlordId") Long landlordId, @Param("status") PropertyStatus status);
	
}
