package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, Long> {
	
	List<IdentityVerification> findByUser(User user);
	
	Optional<IdentityVerification> findByPublicId(UUID publicId);
	
	Optional<IdentityVerification> findByUserAndMethod(User user, IdentityMethod method);
	
	boolean existsByUserAndMethodInAndStatus(User user, Collection<IdentityMethod> methods, IdentityStatus status);
	
	boolean existsByUserAndMethodAndStatus(User user, IdentityMethod method, IdentityStatus status);
	
	/** Other accounts with the same verified value (duplicate detection, index {@code (method, value_hmac)}). */
	@Query("select count(v) from IdentityVerification v where v.method = :method and v.valueHmac = :hmac"
		+ " and v.status = com.rentrix.rentrixserver.identity.IdentityStatus.VERIFIED and v.user <> :user"
		+ " and v.user.status <> :deleted")
	long countVerifiedOnOtherAccounts(@Param("method") IdentityMethod method, @Param("hmac") String hmac,
		@Param("user") User user, @Param("deleted") UserStatus deleted);
	
	/** Other accounts with the same value verified by any of {@code methods} (the phone by SMS or Truecaller). */
	@Query("select count(distinct v.user) from IdentityVerification v where v.method in :methods"
		+ " and v.valueHmac = :hmac and v.status = com.rentrix.rentrixserver.identity.IdentityStatus.VERIFIED"
		+ " and v.user <> :user and v.user.status <> :deleted")
	long countVerifiedOnOtherAccountsIn(@Param("methods") Collection<IdentityMethod> methods,
		@Param("hmac") String hmac, @Param("user") User user, @Param("deleted") UserStatus deleted);
	
}
