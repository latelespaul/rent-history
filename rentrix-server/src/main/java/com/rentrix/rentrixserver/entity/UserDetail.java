package com.rentrix.rentrixserver.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
	name = "user_details",
	indexes = {
		@Index(name = "idx_user_details_telephone", columnList = "telephone", unique = true),
		@Index(name = "idx_user_details_city", columnList = "city")
	}
)
@Data
public class UserDetail extends BaseEntity {
	
	@Id
	private Long id;
	
	@OneToOne(optional = false, fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "id", nullable = false)
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	private User user;
	
	@Column(length = 100)
	private String firstName;
	
	@Column(length = 100)
	private String lastName;
	
	private LocalDate dateOfBirth;
	
	@Column(unique = true, length = 20)
	private String telephone;
	
	@Embedded
	private Address address;
	
}