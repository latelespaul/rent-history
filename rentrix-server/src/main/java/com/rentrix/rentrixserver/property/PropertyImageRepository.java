package com.rentrix.rentrixserver.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, Long> {
	
	Optional<PropertyImage> findByPublicId(UUID publicId);
	
	List<PropertyImage> findByPropertyIdOrderByDisplayOrderAsc(Long propertyId);
	
	long countByPropertyId(Long propertyId);
	
}
