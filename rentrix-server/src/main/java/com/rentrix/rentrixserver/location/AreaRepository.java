package com.rentrix.rentrixserver.location;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AreaRepository extends JpaRepository<Area, Long>, JpaSpecificationExecutor<Area> {
	
	@EntityGraph(attributePaths = {"city", "createdBy"})
	Optional<Area> findByPublicId(UUID publicId);
	
	Optional<Area> findByCityCodeAndNameKey(String cityCode, String nameKey);
	
	boolean existsByDemoTrue();
	
	/** Whether the landlord with {@code landlord} public id has a non-deleted home on this area. */
	@Query("select count(p) > 0 from Property p where p.area.id = :id and p.landlord.publicId = :landlord"
		+ " and p.deletedAt is null")
	boolean holdsHomeOf(@Param("id") Long id, @Param("landlord") UUID landlord);
	
	/** {@code [area id, published homes]} for the given areas (index ix_properties_area_status). */
	@Query("select p.area.id, count(p) from Property p where p.area.id in :ids"
		+ " and p.status = com.rentrix.rentrixserver.property.PropertyStatus.PUBLISHED and p.deletedAt is null"
		+ " group by p.area.id")
	List<Object[]> countPublishedHomes(@Param("ids") Collection<Long> ids);
	
	/** {@code [area id, standalone published homes]}: homes of the area outside any society. */
	@Query("select p.area.id, count(p) from Property p where p.area.id in :ids and p.society is null"
		+ " and p.status = com.rentrix.rentrixserver.property.PropertyStatus.PUBLISHED and p.deletedAt is null"
		+ " group by p.area.id")
	List<Object[]> countStandaloneHomes(@Param("ids") Collection<Long> ids);
	
	/** {@code [area id, active societies]} for the given areas. */
	@Query("select s.area.id, count(s) from Society s where s.area.id in :ids"
		+ " and s.status = com.rentrix.rentrixserver.location.LocationStatus.ACTIVE group by s.area.id")
	List<Object[]> countActiveSocieties(@Param("ids") Collection<Long> ids);
	
}
