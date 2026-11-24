package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.city.City;
import com.rentrix.rentrixserver.common.persistence.PublicEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.EnumSet;
import java.util.Set;

/** A person (§5.1). One Google account = one user (D-72); no password column (D-71). */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends PublicEntity {
	
	@Column(name = "google_sub", unique = true)
	private String googleSub;
	
	/** Lower-cased; null after account deletion (D-58). */
	@Column(name = "email", unique = true, length = 320)
	private String email;
	
	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;
	
	@Column(name = "google_name")
	private String googleName;
	
	@Column(name = "display_name", nullable = false, length = 60)
	private String displayName;
	
	@Column(name = "avatar_url", length = 1024)
	private String avatarUrl;
	
	/** E.164, private (D-57). */
	@Column(name = "phone", length = 20)
	private String phone;
	
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "role", nullable = false, length = 16)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private Set<Role> roles = EnumSet.noneOf(Role.class);
	
	@Column(name = "onboarded_at")
	private Instant onboardedAt;
	
	@Column(name = "last_login_at")
	private Instant lastLoginAt;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "home_city_code")
	private City homeCity;
	
	/** The header's Tenant | Owner mode; null until the user picks one. */
	@Column(name = "preferred_mode", length = 8)
	private String preferredMode;
	
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 16)
	private UserStatus status = UserStatus.ACTIVE;
	
	@Column(name = "is_demo", nullable = false)
	private boolean demo;
	
	@Column(name = "deleted_at")
	private Instant deletedAt;
	
	public boolean hasRole(Role role) {
		return roles.contains(role);
	}
	
}
