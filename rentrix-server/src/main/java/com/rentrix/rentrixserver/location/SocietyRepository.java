package com.rentrix.rentrixserver.location;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocietyRepository extends JpaRepository<Society, Long>, JpaSpecificationExecutor<Society> {
	
	@EntityGraph(attributePaths = {"area", "city", "createdBy"})
	Optional<Society> findByPublicId(UUID publicId);
	
	Optional<Society> findByAreaAndNameKey(Area area, String nameKey);
	
	/** Every society of one city, the candidates of {@link PlaceMatcher} (D-164); the caller drops unlisted ones. */
	@Query("select s from Society s join fetch s.area join fetch s.city c left join fetch s.createdBy"
		+ " where c.code = :city order by s.id")
	List<Society> findCandidatesInCity(@Param("city") String city, Pageable limit);
	
	/** {@link #findCandidatesInCity} for every live city. */
	@Query("select s from Society s join fetch s.area join fetch s.city c left join fetch s.createdBy"
		+ " where c.live = true order by s.id")
	List<Society> findCandidatesInLiveCities(Pageable limit);
	
	/** Whether the landlord with {@code landlord} public id has a non-deleted home on this society. */
	@Query("select count(p) > 0 from Property p where p.society.id = :id and p.landlord.publicId = :landlord"
		+ " and p.deletedAt is null")
	boolean holdsHomeOf(@Param("id") Long id, @Param("landlord") UUID landlord);
	
	/** Whether a published home sits here, so its name is already public on that home's card (D-162, D-166). */
	@Query("select count(p) > 0 from Property p where p.society.id = :id"
		+ " and p.status = com.rentrix.rentrixserver.property.PropertyStatus.PUBLISHED and p.deletedAt is null")
	boolean holdsPublishedHome(@Param("id") Long id);
	
	/** {@code [society id, published homes]} (index ix_properties_society_status). */
	@Query("select p.society.id, count(p) from Property p where p.society.id in :ids"
		+ " and p.status = com.rentrix.rentrixserver.property.PropertyStatus.PUBLISHED and p.deletedAt is null"
		+ " group by p.society.id")
	List<Object[]> countPublishedHomes(@Param("ids") Collection<Long> ids);
	
	/** {@code [society id, active towers]}. */
	@Query("select t.society.id, count(t) from SocietyTower t where t.society.id in :ids"
		+ " and t.status = com.rentrix.rentrixserver.location.LocationStatus.ACTIVE group by t.society.id")
	List<Object[]> countActiveTowers(@Param("ids") Collection<Long> ids);
	
	/** {@code [tower id, wing id or null, published homes]} of one society, for the per-tower counts. */
	@Query("select t.id, w.id, count(p) from Property p join p.tower t left join p.wing w"
		+ " where p.society = :society"
		+ " and p.status = com.rentrix.rentrixserver.property.PropertyStatus.PUBLISHED and p.deletedAt is null"
		+ " group by t.id, w.id")
	List<Object[]> countPublishedHomesByTowerAndWing(@Param("society") Society society);
	
}
