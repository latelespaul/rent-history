package com.rentrix.rentrixserver.entity;

import com.rentrix.rentrixserver.entity.constants.Role;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "users")
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(unique = true)
	@Nullable
	private String username;
	
	@Nullable
	@Column(unique = true)
	private String telephone;
	
	private String password;
	
	@NotBlank
	private String name;
	
	@Nullable
	private String address;
	
	@Email
	@NotBlank
	@Column(unique = true)
	private String email;
	
	@Nullable
	private LocalDate dateOfBirth;
	private Role role;
	
}