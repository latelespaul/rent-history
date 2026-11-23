package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.tenancy.Tenancy;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentalAgreementRepository extends JpaRepository<RentalAgreement, Long> {
	
	Optional<RentalAgreement> findByPublicId(UUID publicId);
	
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from RentalAgreement a where a.publicId = :id")
	Optional<RentalAgreement> lockByPublicId(@Param("id") UUID id);
	
	/** The live agreement of a tenancy: the newest that was not replaced (one per tenancy, §5.20). */
	Optional<RentalAgreement> findFirstByTenancyAndStatusNotOrderByCreatedAtDesc(Tenancy tenancy,
		AgreementStatus status);
	
	/** The live agreements of these tenancies, for list views (one query, not one per tenancy). */
	@Query("select a from RentalAgreement a where a.tenancy.id in :ids and a.status <> :replaced")
	List<RentalAgreement> findLiveByTenancyIds(@Param("ids") Collection<Long> ids,
		@Param("replaced") AgreementStatus replaced);
	
	Page<RentalAgreement> findByStatusIn(Collection<AgreementStatus> statuses, Pageable pageable);
	
	/** Same file on an agreement of another tenancy (D-129). */
	@Query("select count(a) > 0 from RentalAgreement a where a.sha256 = :sha and a.tenancy is not null "
		+ "and a.tenancy <> :tenancy")
	boolean existsSameFileOnOtherTenancy(@Param("sha") String sha256, @Param("tenancy") Tenancy tenancy);
	
	/** Files whose retention time passed and that are still stored (D-131). */
	@Query("select a from RentalAgreement a where a.storageKey is not null and a.purgeAfter <= :now "
		+ "order by a.purgeAfter")
	List<RentalAgreement> findFilesToPurge(@Param("now") Instant now, Pageable pageable);
	
}
