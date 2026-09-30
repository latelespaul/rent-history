package com.rentrix.rentrixserver.entity;

import com.rentrix.rentrixserver.entity.constants.PropertyType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "flats", indexes = {
	@Index(name = "idx_flats_city", columnList = "city"),
	@Index(name = "idx_flats_state", columnList = "state"),
	@Index(name = "idx_flats_rent", columnList = "rent"),
	@Index(name = "idx_flats_available", columnList = "available"),
	@Index(name = "idx_flats_owner", columnList = "owner_id"),
	@Index(name = "idx_flats_property", columnList = "property_type"),
	@Index(name = "idx_flats_deleted", columnList = "deleted")})
@SQLDelete(sql = "UPDATE flats SET deleted = TRUE WHERE id = ?")
@SQLRestriction("deleted = false")
@Data
public class Flat extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal rent;
	
	@Column(nullable = false)
	private Integer numberOfRooms;
	
	@Column(precision = 10, scale = 2)
	private BigDecimal area;
	private Integer floorNumber;
	private Integer totalFloors;
	private Boolean furnished;
	private Integer bathrooms;
	private Boolean parking;
	private LocalDate availableFrom;
	
	@Enumerated(EnumType.STRING)
	@Column(length = 50)
	private PropertyType propertyType;
	
	@Lob
	private String description;
	
	@Column(name = "available", nullable = false)
	private Boolean available = true;
	
	@Embedded
	private Address address;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;
	
	@Column(nullable = false)
	private Boolean deleted = false;
	
}