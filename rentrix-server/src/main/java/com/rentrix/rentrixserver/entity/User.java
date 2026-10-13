package com.rentrix.rentrixserver.entity;

import com.rentrix.rentrixserver.entity.constants.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
	name = "users",
	indexes = {
		@Index(name = "idx_users_email", columnList = "email", unique = true),
		@Index(name = "idx_users_role", columnList = "role"),
		@Index(name = "idx_users_deleted", columnList = "deleted")
	}
)
@SQLDelete(sql = "UPDATE users SET deleted = TRUE WHERE id = ?")
@SQLRestriction("deleted = false")
@Data
public class User extends BaseEntity implements UserDetails {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@NotBlank
	@Size(min = 2, max = 100)
	@Column(nullable = false)
	private String name;
	
	@NotBlank
	@Email
	@Column(unique = true, nullable = false)
	private String email;
	
	@NotBlank
	@Column(nullable = false)
	private String password;
	
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;
	
	@Column(nullable = false)
	private Boolean deleted = false;
	
	@OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	private UserDetail userDetail;
	
	// ── UserDetails implementation ──────────────────────────────────────
	
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (role == null) return List.of();
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}
	
	@Override
	public String getUsername() {
		return email;
	}
	
	@Override
	public boolean isAccountNonExpired() {return true;}
	
	@Override
	public boolean isAccountNonLocked() {return true;}
	
	@Override
	public boolean isCredentialsNonExpired() {return true;}
	
	@Override
	public boolean isEnabled() {
		return !Boolean.TRUE.equals(deleted);
	}
	
	// ── Display helper ──────────────────────────────────────────────────
	
	/**
	 * Best-effort display name for UI. Never returns null.
	 * Priority: name field → firstName + lastName from userDetail → email.
	 */
	public String getDisplayName() {
		if (name != null && !name.isBlank()) return name;
		if (userDetail == null) return email;
		String first = userDetail.getFirstName() != null ? userDetail.getFirstName() : "";
		String last = userDetail.getLastName() != null ? userDetail.getLastName() : "";
		String full = (first + " " + last).trim();
		return full.isEmpty() ? email : full;
	}
	
}