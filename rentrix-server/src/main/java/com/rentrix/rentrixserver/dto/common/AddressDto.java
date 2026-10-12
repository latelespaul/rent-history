package com.rentrix.rentrixserver.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {
	
	@Size(max = 255, message = "Address line too long")
	private String addressLine;
	
	@Size(max = 255, message = "Street too long")
	private String street;
	
	@NotBlank(message = "City is required")
	@Size(max = 100, message = "City too long")
	private String city;
	
	@Size(max = 100, message = "State too long")
	private String state;
	
	@Size(max = 20, message = "Zip code too long")
	private String zipCode;
	
}