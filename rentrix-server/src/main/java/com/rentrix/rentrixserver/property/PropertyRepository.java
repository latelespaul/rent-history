package com.rentrix.rentrixserver.property;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, Long> {
	
	Optional<Property> findByPublicId(UUID publicId);
	
	/** Search by city (index ix_properties_city_status_locality). */
	Page<Property> findByCityCodeAndStatusAndDeletedAtIsNull(String cityCode, PropertyStatus status, Pageable pageable);
	
	/** Search by city and locality prefix on the normalised key (same index). */
	Page<Property> findByCityCodeAndStatusAndLocalityKeyStartingWithAndDeletedAtIsNull(String cityCode,
		PropertyStatus status, String localityKeyPrefix, Pageable pageable);
	
	Page<Property> findByLandlordIdAndDeletedAtIsNull(Long landlordId, Pageable pageable);
	
}
