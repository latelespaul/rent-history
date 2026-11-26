package com.rentrix.rentrixserver.invite;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A person who signed up through another user's code (invite-friends IF-2, IF-5); one per invitee, ever. */
@Entity
@Table(name = "referrals")
@Getter
@Setter
public class Referral extends BaseEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inviter_id", nullable = false)
	private User inviter;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invitee_id", nullable = false, unique = true)
	private User invitee;
	
}
