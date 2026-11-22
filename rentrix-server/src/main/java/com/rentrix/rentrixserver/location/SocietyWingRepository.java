package com.rentrix.rentrixserver.location;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocietyWingRepository extends JpaRepository<SocietyWing, Long> {
	
	@EntityGraph(attributePaths = {"tower", "createdBy"})
	Optional<SocietyWing> findByPublicId(UUID publicId);
	
	Optional<SocietyWing> findByTowerAndNameKey(SocietyTower tower, String nameKey);
	
}
