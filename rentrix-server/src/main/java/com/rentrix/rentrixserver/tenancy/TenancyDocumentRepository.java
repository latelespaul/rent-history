package com.rentrix.rentrixserver.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenancyDocumentRepository extends JpaRepository<TenancyDocument, Long> {
	
	Optional<TenancyDocument> findByPublicId(UUID publicId);
	
	List<TenancyDocument> findByTenancyIdOrderByCreatedAtDesc(Long tenancyId);
	
}
