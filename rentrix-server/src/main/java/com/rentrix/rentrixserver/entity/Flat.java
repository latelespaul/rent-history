package com.rentrix.rentrixserver.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "flats")
@Data
public class Flat {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	private String address;
	private String city;
	private String state;
	
	private Integer numberOfRooms;
	private BigDecimal area; // in square meters
	private BigDecimal rent; // in currency
	
	@Lob
	private String description;
	private Boolean available;
	
}