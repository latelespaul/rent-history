package com.rentrix.rentrixserver.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
	
	Optional<User> findByPublicId(UUID publicId);
	
	Optional<User> findByGoogleSub(String googleSub);
	
	Optional<User> findByEmail(String email);
	
	/** Seed users for the dev-login picker (D-77). */
	List<User> findByDemoTrueAndStatusOrderByDisplayNameAsc(UserStatus status);
	
}
