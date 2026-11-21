package com.rentrix.rentrixserver.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenancyRepository extends JpaRepository<Tenancy, Long> {
	
	Optional<Tenancy> findByPublicId(UUID publicId);
	
	List<Tenancy> findByPropertyIdAndStatusIn(Long propertyId, List<TenancyStatus> statuses);
	
	List<Tenancy> findByTenantIdAndStatusIn(Long tenantId, List<TenancyStatus> statuses);
	
	List<Tenancy> findByLandlordIdAndStatusIn(Long landlordId, List<TenancyStatus> statuses);
	
	/** All tenancies of one landlord–tenant pair, newest first (repeat-pair rule, D-44). */
	List<Tenancy> findByLandlordIdAndTenantIdOrderByStartDateDesc(Long landlordId, Long tenantId);
	
}
