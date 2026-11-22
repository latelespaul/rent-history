package com.rentrix.rentrixserver.tenancy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenancyRepository extends JpaRepository<Tenancy, Long> {
	
	Optional<Tenancy> findByPublicId(UUID publicId);
	
	List<Tenancy> findByPropertyIdAndStatusIn(Long propertyId, List<TenancyStatus> statuses);
	
	List<Tenancy> findByTenantIdAndStatusIn(Long tenantId, List<TenancyStatus> statuses);
	
	List<Tenancy> findByLandlordIdAndStatusIn(Long landlordId, List<TenancyStatus> statuses);
	
	/** All tenancies of one landlord–tenant pair, newest first (repeat-pair rule, D-44). */
	List<Tenancy> findByLandlordIdAndTenantIdOrderByStartDateDesc(Long landlordId, Long tenantId);
	
	/** {@code /me/tenancies?role=LANDLORD}. */
	Page<Tenancy> findByLandlordIdAndStatusIn(Long landlordId, Collection<TenancyStatus> statuses, Pageable pageable);
	
	/** {@code /me/tenancies?role=TENANT}. */
	Page<Tenancy> findByTenantIdAndStatusIn(Long tenantId, Collection<TenancyStatus> statuses, Pageable pageable);
	
	/** {@code /me/tenancies}: either side. */
	@Query("select t from Tenancy t left join t.tenant tn "
		+ "where (t.landlord.id = :userId or tn.id = :userId) and t.status in :statuses")
	Page<Tenancy> findByPartyAndStatusIn(@Param("userId") Long userId,
		@Param("statuses") Collection<TenancyStatus> statuses, Pageable pageable);
	
	/**
	 * D-23: another live tenancy of the same tenant on the same property whose dates overlap
	 * {@code [start, end]} ({@code end} = far future for a current tenancy).
	 */
	@Query("select count(t) > 0 from Tenancy t where t.property.id = :propertyId and t.tenant.id = :tenantId "
		+ "and t.id <> :excludeId and t.status in :statuses "
		+ "and t.startDate <= :end and (t.endDate is null or t.endDate >= :start)")
	boolean existsOverlap(@Param("propertyId") Long propertyId, @Param("tenantId") Long tenantId,
		@Param("excludeId") Long excludeId, @Param("statuses") Collection<TenancyStatus> statuses,
		@Param("start") LocalDate start, @Param("end") LocalDate end);
	
	/** D-35 pending cap: pending requests this user started, on either side. */
	@Query("select count(t) from Tenancy t left join t.tenant tn where t.status = :status and ("
		+ "(t.initiatedBy = com.rentrix.rentrixserver.tenancy.TenancyParty.LANDLORD and t.landlord.id = :userId) or "
		+ "(t.initiatedBy = com.rentrix.rentrixserver.tenancy.TenancyParty.TENANT and tn.id = :userId))")
	long countInitiatedBy(@Param("userId") Long userId, @Param("status") TenancyStatus status);
	
	/** Pending requests older than the 14-day confirmation window (§6). */
	List<Tenancy> findByStatusAndCreatedAtBefore(TenancyStatus status, Instant cutoff);
	
}
