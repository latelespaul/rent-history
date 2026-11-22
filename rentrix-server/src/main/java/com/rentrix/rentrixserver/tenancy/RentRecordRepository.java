package com.rentrix.rentrixserver.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentRecordRepository extends JpaRepository<RentRecord, Long> {
	
	Optional<RentRecord> findByPublicId(UUID publicId);
	
	List<RentRecord> findByTenancyIdOrderByEffectiveFromAsc(Long tenancyId);
	
	/** Public rent history of a property: confirmed rows of its confirmed tenancies (D-26). */
	List<RentRecord> findByTenancyPropertyIdAndStatusAndTenancyStatusInOrderByEffectiveFromAsc(Long propertyId,
		RentRecordStatus status, List<TenancyStatus> tenancyStatuses);
	
	/** A live (proposed or confirmed) record for the same month already exists. */
	boolean existsByTenancyIdAndEffectiveFromAndStatusIn(Long tenancyId, LocalDate effectiveFrom,
		Collection<RentRecordStatus> statuses);
	
}
