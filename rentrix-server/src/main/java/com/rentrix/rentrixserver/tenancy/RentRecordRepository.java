package com.rentrix.rentrixserver.tenancy;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentRecordRepository extends JpaRepository<RentRecord, Long> {
	
	Optional<RentRecord> findByPublicId(UUID publicId);
	
	List<RentRecord> findByTenancyIdOrderByEffectiveFromAsc(Long tenancyId);
	
	/** The rent records of several homes, for the society rent range (D-166). */
	@EntityGraph(attributePaths = {"tenancy", "tenancy.property"})
	List<RentRecord> findByTenancyPropertyIdInAndStatusInAndTenancyStatusIn(Collection<Long> propertyIds,
		Collection<RentRecordStatus> statuses, Collection<TenancyStatus> tenancyStatuses);
	
	/**
	 * Public rent history of a property: rows in the given states of its confirmed tenancies (D-26; also
	 * {@code PROPOSED} on an unclaimed home, D-139).
	 */
	List<RentRecord> findByTenancyPropertyIdAndStatusInAndTenancyStatusInOrderByEffectiveFromAsc(Long propertyId,
		Collection<RentRecordStatus> statuses, List<TenancyStatus> tenancyStatuses);
	
	/** A live (proposed or confirmed) record for the same month already exists. */
	boolean existsByTenancyIdAndEffectiveFromAndStatusIn(Long tenancyId, LocalDate effectiveFrom,
		Collection<RentRecordStatus> statuses);
	
}
