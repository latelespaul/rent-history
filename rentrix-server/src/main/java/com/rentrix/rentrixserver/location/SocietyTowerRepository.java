package com.rentrix.rentrixserver.location;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocietyTowerRepository extends JpaRepository<SocietyTower, Long> {
	
	@EntityGraph(attributePaths = {"society", "createdBy"})
	Optional<SocietyTower> findByPublicId(UUID publicId);
	
	List<SocietyTower> findBySocietyOrderBySortOrderAscNameKeyAsc(Society society);
	
	Optional<SocietyTower> findBySocietyAndNameKey(Society society, String nameKey);
	
	long countBySociety(Society society);
	
}
