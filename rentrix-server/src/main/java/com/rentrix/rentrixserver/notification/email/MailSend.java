package com.rentrix.rentrixserver.notification.email;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One campaign email a user got (D-172): unique per user and campaign, so no campaign mails anyone twice. */
@Entity
@Table(name = "mail_sends")
@Getter
@NoArgsConstructor
public class MailSend extends BaseEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	
	@Column(name = "campaign", nullable = false, length = 40)
	private String campaign;
	
	MailSend(User user, String campaign) {
		this.user = user;
		this.campaign = campaign;
	}
	
}
