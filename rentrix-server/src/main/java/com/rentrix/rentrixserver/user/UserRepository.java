package com.rentrix.rentrixserver.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
	
	Optional<User> findByPublicId(UUID publicId);
	
	/** The user row locked for update until the transaction ends, to serialise per-user rate limits. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.publicId = :publicId")
	Optional<User> lockByPublicId(@Param("publicId") UUID publicId);
	
	Optional<User> findByGoogleSub(String googleSub);
	
	Optional<User> findByEmail(String email);
	
	/** Seed users for the dev-login picker (D-77). */
	List<User> findByDemoTrueAndStatusOrderByDisplayNameAsc(UserStatus status);
	
}
