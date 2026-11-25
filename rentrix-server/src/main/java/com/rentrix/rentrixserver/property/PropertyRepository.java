package com.rentrix.rentrixserver.property;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, Long> {
	
	/** The property row locked for update, to serialise tenancy writes on one home (overlap guard). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Property p where p.id = :id")
	Optional<Property> lockById(@Param("id") Long id);
	
	Optional<Property> findByPublicId(UUID publicId);
	
	/** A property that is not soft-deleted, with its city and landlord loaded. */
	@EntityGraph(attributePaths = {"city", "landlord"})
	Optional<Property> findByPublicIdAndDeletedAtIsNull(UUID publicId);
	
	/** Loads one search page (ids from {@link PropertySearchQuery}) with city, landlord and place. */
	@EntityGraph(attributePaths = {"city", "landlord", "area", "society", "tower", "wing"})
	List<Property> findByIdIn(Collection<Long> ids);
	
	/** Search by city (index ix_properties_city_status_locality). */
	Page<Property> findByCityCodeAndStatusAndDeletedAtIsNull(String cityCode, PropertyStatus status, Pageable pageable);
	
	/** Search by city and locality prefix on the normalised key (same index). */
	Page<Property> findByCityCodeAndStatusAndLocalityKeyStartingWithAndDeletedAtIsNull(String cityCode,
		PropertyStatus status, String localityKeyPrefix, Pageable pageable);
	
	/** The owner's own properties, every status (index ix_properties_landlord). */
	@EntityGraph(attributePaths = {"city", "landlord"})
	Page<Property> findByLandlordIdAndDeletedAtIsNull(Long landlordId, Pageable pageable);
	
	/** Candidates for the "Is it this one?" check of {@code POST /me/homes} (D-139). */
	List<Property> findByCityCodeAndPincodeAndStatusAndDeletedAtIsNull(String cityCode, String pincode,
		PropertyStatus status);
	
	/**
	 * The published homes of the given societies whose flat number is {@code visibility}; {@link FlatFinder} asks
	 * only for {@code OPEN} ones, so a masked flat number is never matched (D-57, D-164).
	 */
	@EntityGraph(attributePaths = {"city", "society", "tower", "wing"})
	List<Property> findBySocietyIdInAndStatusAndUnitLabelVisibilityAndDeletedAtIsNull(Collection<Long> societyIds,
		PropertyStatus status, FieldVisibility visibility);
	
	/** Internal ids of a society's homes in {@code status}, not deleted (D-166 society summary). */
	@Query("select p.id from Property p where p.society.id = :society and p.status = :status and p.deletedAt is null")
	List<Long> findIdsBySocietyAndStatus(@Param("society") Long societyId, @Param("status") PropertyStatus status);
	
}
