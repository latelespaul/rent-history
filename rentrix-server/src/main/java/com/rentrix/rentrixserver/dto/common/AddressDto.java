package com.rentrix.rentrixserver.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {
	private String addressLine;
	private String street;
	private String city;
	private String state;
	private String zipCode;
	
}