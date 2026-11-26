package com.rentrix.rentrixserver.invite;

import com.rentrix.rentrixserver.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
	
	long countByInviter(User inviter);
	
	boolean existsByInvitee(User invitee);
	
}
