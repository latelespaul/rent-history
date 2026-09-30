package com.rentrix.rentrixserver.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "user_details")
@Data
public class UserDetail extends BaseEntity {
	@Id
	private Long id;
	
	@OneToOne(optional = false, fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "id", nullable = false)
	@EqualsAndHashCode.Exclude    // ← breaks the cycle
	@ToString.Exclude             // ← also for @Data's toString
	private User user;
	
	private String firstName;
	private String lastName;
	private LocalDate dateOfBirth;
	
	@Column(unique = true)
	private String telephone;
	
	@Embedded
	private Address address;
	
}