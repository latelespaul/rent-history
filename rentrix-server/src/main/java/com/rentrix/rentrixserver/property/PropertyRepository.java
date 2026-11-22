package com.rentrix.rentrixserver.property;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, Long> {
	
	Optional<Property> findByPublicId(UUID publicId);
	
	/** A property that is not soft-deleted, with its city and landlord loaded. */
	@EntityGraph(attributePaths = {"city", "landlord"})
	Optional<Property> findByPublicIdAndDeletedAtIsNull(UUID publicId);
	
	/** Loads one search page (ids from {@link PropertySearchQuery}) with city and landlord. */
	@EntityGraph(attributePaths = {"city", "landlord"})
	List<Property> findByIdIn(Collection<Long> ids);
	
	/** Search by city (index ix_properties_city_status_locality). */
	Page<Property> findByCityCodeAndStatusAndDeletedAtIsNull(String cityCode, PropertyStatus status, Pageable pageable);
	
	/** Search by city and locality prefix on the normalised key (same index). */
	Page<Property> findByCityCodeAndStatusAndLocalityKeyStartingWithAndDeletedAtIsNull(String cityCode,
		PropertyStatus status, String localityKeyPrefix, Pageable pageable);
	
	/** The owner's own properties, every status (index ix_properties_landlord). */
	@EntityGraph(attributePaths = {"city", "landlord"})
	Page<Property> findByLandlordIdAndDeletedAtIsNull(Long landlordId, Pageable pageable);
	
}
