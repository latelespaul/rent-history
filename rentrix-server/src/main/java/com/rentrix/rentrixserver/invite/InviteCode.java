package com.rentrix.rentrixserver.invite;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A user's random share code (invite-friends IF-1, IF-3). Kept off {@code users}, so no view of a user can carry it;
 * only {@code GET /me/invite} returns it, to its owner.
 */
@Entity
@Table(name = "invite_codes")
@Getter
@Setter
public class InviteCode extends BaseEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;
	
	@Column(name = "code", nullable = false, unique = true, length = 16)
	private String code;
	
}
