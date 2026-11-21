package com.rentrix.rentrixserver.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
	
	Optional<RefreshToken> findByTokenHash(String tokenHash);
	
	List<RefreshToken> findByFamilyId(UUID familyId);
	
	/** Revokes the token only if it is still active; returns 0 when another request revoked it first. */
	@Modifying(flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now where t.id = :id and t.revokedAt is null")
	int revokeIfActive(@Param("id") Long id, @Param("now") Instant now);
	
	/** Read from the database, not the persistence context, so a concurrent revocation is visible. */
	@Query("select t.revokedAt from RefreshToken t where t.id = :id")
	Instant findRevokedAtById(@Param("id") Long id);
	
	boolean existsByFamilyIdAndRevokedAtAfter(UUID familyId, Instant revokedAt);
	
	boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(UUID familyId, Instant now);
	
}
