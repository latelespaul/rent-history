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
	
	/**
	 * Revokes the token with this hash if it is still active and unexpired; returns 0 otherwise. One UPDATE with no
	 * read before it in the transaction: on SQLite a transaction that reads and then writes fails at once with
	 * SQLITE_BUSY when another connection committed a write in between, while one that starts with its write waits
	 * for the busy timeout (D-11).
	 */
	@Modifying(flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now "
		+ "where t.tokenHash = :hash and t.revokedAt is null and t.expiresAt > :now")
	int revokeActiveByHash(@Param("hash") String hash, @Param("now") Instant now);
	
	/** Read from the database, not the persistence context, so a concurrent revocation is visible. */
	@Query("select t.revokedAt from RefreshToken t where t.id = :id")
	Instant findRevokedAtById(@Param("id") Long id);
	
	boolean existsByFamilyIdAndRevokedAtAfter(UUID familyId, Instant revokedAt);
	
	boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(UUID familyId, Instant now);
	
}
