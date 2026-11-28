package com.rentrix.rentrixserver.feedback;

import com.rentrix.rentrixserver.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One message from the "Contact us" form. Name and email are optional; nothing links it to a user. */
@Entity
@Table(name = "feedback")
@Getter
@NoArgsConstructor
public class Feedback extends BaseEntity {
	
	@Column(name = "name", length = 80)
	private String name;
	
	@Column(name = "email", length = 320)
	private String email;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 16)
	private FeedbackType type;
	
	@Column(name = "message", nullable = false, length = 2000)
	private String message;
	
	Feedback(String name, String email, FeedbackType type, String message) {
		this.name = name;
		this.email = email;
		this.type = type;
		this.message = message;
	}
	
}
