package com.rentrix.rentrixserver.verification;

import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationApplicationRepository extends JpaRepository<VerificationApplication, Long> {
	
	List<VerificationApplication> findByUser(User user);
	
	Optional<VerificationApplication> findByUserAndRole(User user, Role role);
	
	Optional<VerificationApplication> findByPublicId(UUID publicId);
	
}
