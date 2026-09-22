package com.rentrix.rentrixserver.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FlatDto {
	private String address;
	private String city;
	private String state;
	
	private Integer numberOfRooms;
	private BigDecimal area;
	private BigDecimal rent;
	
	private String description;
	private Boolean isAvailable;
	
}