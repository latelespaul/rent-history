package com.rentrix.rentrixserver.identity;

import com.rentrix.rentrixserver.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhoneOtpChallengeRepository extends JpaRepository<PhoneOtpChallenge, Long> {
	
	Optional<PhoneOtpChallenge> findByPublicIdAndUser(UUID publicId, User user);
	
	/** Send times for the per-user limits, oldest first (index {@code (user_id, created_at)}). */
	@Query("select c.createdAt from PhoneOtpChallenge c where c.user = :user and c.createdAt > :since"
		+ " order by c.createdAt")
	List<Instant> sentToUserSince(@Param("user") User user, @Param("since") Instant since);
	
	/** Send times for the per-number limit across accounts, oldest first (index {@code (phone_hmac, created_at)}). */
	@Query("select c.createdAt from PhoneOtpChallenge c where c.phoneHmac = :phoneHmac and c.createdAt > :since"
		+ " order by c.createdAt")
	List<Instant> sentToPhoneSince(@Param("phoneHmac") String phoneHmac, @Param("since") Instant since);
	
	/** Counts one wrong code; 0 when the challenge already has {@code max} attempts. */
	@Modifying
	@Transactional
	@Query("update PhoneOtpChallenge c set c.attempts = c.attempts + 1, c.updatedAt = :now"
		+ " where c.id = :id and c.attempts < :max")
	int countWrongAttempt(@Param("id") Long id, @Param("max") int max, @Param("now") Instant now);
	
	/** Uses the challenge up; 0 when it is already used, expired or out of attempts. */
	@Modifying
	@Transactional
	@Query("update PhoneOtpChallenge c set c.consumedAt = :now, c.updatedAt = :now where c.id = :id and c.consumedAt is null"
		+ " and c.attempts < :max and c.expiresAt > :now")
	int consume(@Param("id") Long id, @Param("max") int max, @Param("now") Instant now);
	
	/** Deletes challenges that expired, or were used, before {@code cutoff}. */
	@Modifying
	@Transactional
	@Query("delete from PhoneOtpChallenge c where c.expiresAt < :cutoff or c.consumedAt < :cutoff")
	int deleteFinishedBefore(@Param("cutoff") Instant cutoff);
	
}
