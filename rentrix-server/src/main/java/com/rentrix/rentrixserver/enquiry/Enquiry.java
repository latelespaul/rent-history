package com.rentrix.rentrixserver.enquiry;

import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A tenant's enquiry about a property (§5.9). Not a chat: it is the grant that lets the
 * landlord see the tenant's profile and tenant reputation until {@code expiresAt} (D-31, D-54).
 */
@Entity
@Table(name = "enquiries")
@Getter
@Setter
public class Enquiry extends PublicEntity {
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private User tenant;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;
	
	@Column(name = "message", nullable = false, length = 1000)
	private String message;
	
	@Column(name = "share_contact", nullable = false)
	private boolean shareContact;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private EnquiryStatus status = EnquiryStatus.OPEN;
	
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	
}
