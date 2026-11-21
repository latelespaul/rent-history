package com.rentrix.rentrixserver.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
	
	Optional<ShareLink> findByTokenHash(String tokenHash);
	
	List<ShareLink> findByUserIdOrderByCreatedAtDesc(Long userId);
	
}
