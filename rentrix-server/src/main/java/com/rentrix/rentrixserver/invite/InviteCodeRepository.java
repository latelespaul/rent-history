package com.rentrix.rentrixserver.invite;

import com.rentrix.rentrixserver.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {
	
	Optional<InviteCode> findByUser(User user);
	
	Optional<InviteCode> findByCode(String code);
	
	boolean existsByCode(String code);
	
}
