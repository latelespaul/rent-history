package com.rentrix.rentrixserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class Address {
	
	@Column(name = "address_line", length = 255)
	private String addressLine;
	
	@Column(length = 255)
	private String street;
	
	@Column(length = 100)
	private String city;
	
	@Column(length = 100)
	private String state;
	
	@Column(name = "zip_code", length = 20)
	private String zipCode;
	
}