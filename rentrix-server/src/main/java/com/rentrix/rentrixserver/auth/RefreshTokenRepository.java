package com.rentrix.rentrixserver.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
	
	Optional<RefreshToken> findByTokenHash(String tokenHash);
	
	List<RefreshToken> findByFamilyId(UUID familyId);
	
}
