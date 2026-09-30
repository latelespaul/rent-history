package com.rentrix.rentrixserver.dto.request;

import com.rentrix.rentrixserver.dto.common.AddressDto;
import com.rentrix.rentrixserver.entity.constants.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateFlatRequest {
	
	@NotNull(message = "Rent is required")
	@DecimalMin(value = "0", inclusive = false, message = "Rent must be positive")
	private BigDecimal rent;
	
	@NotNull(message = "Number of rooms is required")
	@Min(value = 1, message = "At least 1 room required")
	@Max(value = 20, message = "Unreasonable number of rooms")
	private Integer numberOfRooms;
	
	@Positive(message = "Area must be positive")
	private BigDecimal area;
	
	@Min(value = 0, message = "Floor number must be >= 0")
	private Integer floorNumber;
	
	@Min(value = 1, message = "Total floors must be >= 1")
	private Integer totalFloors;
	
	private Boolean furnished;
	
	@Min(value = 1, message = "At least 1 bathroom")
	@Max(value = 20, message = "Unreasonable number of bathrooms")
	private Integer bathrooms;
	
	private Boolean parking;
	
	private LocalDate availableFrom;
	
	@NotNull(message = "Property type is required")
	private PropertyType propertyType;
	
	@Size(max = 4000, message = "Description must be under 4000 characters")
	private String description;
	
	private Boolean available = true;
	
	@NotNull(message = "Address is required")
	@Valid
	private AddressDto address;
	
	// NOTE: owner is intentionally omitted — derived from JWT
}