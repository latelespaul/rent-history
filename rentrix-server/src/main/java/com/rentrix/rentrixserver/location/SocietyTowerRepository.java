package com.rentrix.rentrixserver.location;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocietyTowerRepository extends JpaRepository<SocietyTower, Long> {
	
	@EntityGraph(attributePaths = {"society", "createdBy"})
	Optional<SocietyTower> findByPublicId(UUID publicId);
	
	List<SocietyTower> findBySocietyOrderBySortOrderAscNameKeyAsc(Society society);
	
	Optional<SocietyTower> findBySocietyAndNameKey(Society society, String nameKey);
	
	/** Whether the landlord with {@code landlord} public id has a non-deleted home on this tower. */
	@Query("select count(p) > 0 from Property p where p.tower.id = :id and p.landlord.publicId = :landlord"
		+ " and p.deletedAt is null")
	boolean holdsHomeOf(@Param("id") Long id, @Param("landlord") UUID landlord);
	
	long countBySociety(Society society);
	
}
