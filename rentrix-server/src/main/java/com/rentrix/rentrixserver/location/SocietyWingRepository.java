package com.rentrix.rentrixserver.location;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SocietyWingRepository extends JpaRepository<SocietyWing, Long> {
	
	@EntityGraph(attributePaths = {"tower", "createdBy"})
	Optional<SocietyWing> findByPublicId(UUID publicId);
	
	Optional<SocietyWing> findByTowerAndNameKey(SocietyTower tower, String nameKey);
	
	/** Whether the landlord with {@code landlord} public id has a non-deleted home on this wing. */
	@Query("select count(p) > 0 from Property p where p.wing.id = :id and p.landlord.publicId = :landlord"
		+ " and p.deletedAt is null")
	boolean holdsHomeOf(@Param("id") Long id, @Param("landlord") UUID landlord);
	
}
