package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MailSendRepository extends JpaRepository<MailSend, Long> {
	
	boolean existsByUserAndCampaign(User user, String campaign);
	
	/**
	 * Who may get {@code campaign} (D-172): active, not demo, not deleted, with a verified email, not opted out and
	 * not mailed this campaign before. {@code excludedEmail} is the unclaimed-homes placeholder.
	 */
	@Query("""
		select u from User u
		where u.email is not null and u.email <> :excludedEmail and u.emailVerifiedAt is not null
			and u.deletedAt is null and u.demo = false and u.mailOptOutAt is null
			and u.status = com.rentrix.rentrixserver.user.UserStatus.ACTIVE
			and not exists (select s.id from MailSend s where s.user = u and s.campaign = :campaign)
		order by u.id""")
	List<User> campaignRecipients(String campaign, String excludedEmail);
	
	/** {@link #campaignRecipients} without anyone who has written a review (deleted reviews do not count). */
	@Query("""
		select u from User u
		where u.email is not null and u.email <> :excludedEmail and u.emailVerifiedAt is not null
			and u.deletedAt is null and u.demo = false and u.mailOptOutAt is null
			and u.status = com.rentrix.rentrixserver.user.UserStatus.ACTIVE
			and not exists (select s.id from MailSend s where s.user = u and s.campaign = :campaign)
			and not exists (select r.id from Review r where r.author = u and r.deletedAt is null)
		order by u.id""")
	List<User> campaignRecipientsWithoutReview(String campaign, String excludedEmail);
	
}
