package com.rentrix.rentrixserver.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InviteTokenRepository extends JpaRepository<InviteToken, Long> {
	
	Optional<InviteToken> findByTokenHash(String tokenHash);
	
	List<InviteToken> findByTenancyIdAndUsedAtIsNull(Long tenancyId);
	
}
